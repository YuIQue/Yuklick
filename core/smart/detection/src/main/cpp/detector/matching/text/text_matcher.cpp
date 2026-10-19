// Modified for Yuklick on 2026-10-20. See CHANGELOG.md.
/*
 * Copyright (C) 2026 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/imgproc/imgproc_c.h>
#include <cctype>
#include <algorithm>
#include <limits>

#include "text_matcher.hpp"
#include "../../../logs/log.h"
#include "../../../utils/roi.h"

using namespace smartautoclicker;

namespace {
// OCR dictionaries contain UTF-8 text. Compare Unicode scalar values, otherwise a
// single mistaken Han character counts as three edits and short Chinese labels fail.
std::u32string toCodePoints(const std::string& utf8) {
    std::u32string result;
    result.reserve(utf8.size());
    for (size_t i = 0; i < utf8.size();) {
        const auto first = static_cast<unsigned char>(utf8[i]);
        char32_t point = 0;
        size_t length = 0;
        if (first < 0x80) { point = first; length = 1; }
        else if ((first & 0xe0) == 0xc0) { point = first & 0x1f; length = 2; }
        else if ((first & 0xf0) == 0xe0) { point = first & 0x0f; length = 3; }
        else if ((first & 0xf8) == 0xf0) { point = first & 0x07; length = 4; }

        if (length == 0 || i + length > utf8.size()) {
            result.push_back(0xfffd);
            ++i;
            continue;
        }
        bool valid = true;
        for (size_t j = 1; j < length; ++j) {
            const auto next = static_cast<unsigned char>(utf8[i + j]);
            if ((next & 0xc0) != 0x80) { valid = false; break; }
            point = (point << 6) | (next & 0x3f);
        }
        if (!valid || (length == 2 && point < 0x80) ||
            (length == 3 && point < 0x800) || (length == 4 && point < 0x10000) ||
            (point >= 0xd800 && point <= 0xdfff) || point > 0x10ffff) {
            result.push_back(0xfffd);
            ++i;
        } else {
            result.push_back(point);
            i += length;
        }
    }
    return result;
}
}

bool TextMatcher::init(const std::string& detectionModelPath, const std::map<std::string, std::string>& recognitionModels) {
    if (!recognitionModels.empty()) {
        defaultRecognitionModelId = recognitionModels.begin()->first;
    }
    return textLocator->init(detectionModelPath) && textRecognizer->init(recognitionModels);
}

bool TextMatcher::isInitialized() const {
    return textLocator->isInitialized && textRecognizer->isInitialized;
}

void TextMatcher::clearResults() {
    currentMatchingResult.reset();
}

TextMatchingResult* TextMatcher::matchText(
        const ScreenImage& screenImage,
        const std::string& conditionText,
        const std::string& recognitionModelId,
        const cv::Rect& detectionArea,
        int threshold)
{
    clearResults();

    if (!isInitialized() || !isRoiValidForMatching(screenImage.getRoi(), detectionArea)) {
        LOGE("TextMatcher", "Can't match text, invalid state or RoI (x=%d, y=%d, w=%d, h=%d)",
             detectionArea.x, detectionArea.y, detectionArea.width, detectionArea.height);
        return &currentMatchingResult;
    }

    // Recognize the text in the regions detected
    auto recognizerResults = recognizeText(screenImage, detectionArea, recognitionModelId);

    // Parse results and find matching candidate, if any
    for (const auto& recognizerResult: recognizerResults) {
        float score = bestSubstringSimilarity(recognizerResult.text,conditionText) * 100;
        LOGD("TextMatcher", "Score=%f; recognized=%s", score, recognizerResult.text.c_str());

        if (score < currentMatchingResult.getResultConfidence()) continue;

        currentMatchingResult.updateResults(detectionArea, recognizerResult.boundingBox, score);
        if ((int) score >= threshold) {
            currentMatchingResult.markResultAsDetected();
            break;
        }
    }

    return &currentMatchingResult;
}

TextMatchingResult* TextMatcher::matchNumber(
        const ScreenImage& screenImage,
        const cv::Rect& detectionArea,
        int threshold,
        NumberFormat numberFormat
) {
    clearResults();

    if (!isInitialized() || !isRoiValidForMatching(screenImage.getRoi(), detectionArea)) {
        LOGE("TextMatcher", "Can't match text, invalid state or RoI (x=%d, y=%d, w=%d, h=%d)",
             detectionArea.x, detectionArea.y, detectionArea.width, detectionArea.height);
        return &currentMatchingResult;
    }

    if (defaultRecognitionModelId.empty()) {
        LOGE("TextMatcher", "Can't match number, no recognition model available");
        return &currentMatchingResult;
    }

    // Recognize the text in the detectionArea
    auto recognizerResults = recognizeText(screenImage, detectionArea, defaultRecognitionModelId);

    // Parse results and find matching candidate, if any
    for (const auto& recognizerResult: recognizerResults) {
        if (!isNumber(recognizerResult.text)) continue;

        float score = recognizerResult.confidence * 100;
        auto recognizedNumber = stringToDouble(recognizerResult.text, numberFormat);
        LOGD("TextMatcher", "Score=%f; recognized=%f", score, recognizedNumber);

        // Score is below the best confidence, skip
        if (score < currentMatchingResult.getResultConfidence()) continue;

        currentMatchingResult.updateResults(
                detectionArea,
                recognizerResult.boundingBox,
                score,
                recognizedNumber);

        if ((int) score >= threshold) {
            currentMatchingResult.markResultAsDetected();
        }
    }

    return &currentMatchingResult;
}

bool TextMatcher::isRoiValidForMatching(const cv::Rect& screenRoi, const cv::Rect& roi) {
    if (roi.width <= 0 || roi.height <= 0 || !isRoiContainsOrEquals(screenRoi, roi)) {
        LOGD("TextMatcher", "Can't detect text, detection area (x=%d, y=%d, w=%d, h=%d) is not contained in screen (w=%d, h=%d)",
             roi.x, roi.y, roi.width, roi.height,
             screenRoi.width, screenRoi.height);
        return false;
    }

    return true;
}

std::vector<TextRecognizerResult> TextMatcher::recognizeText(
        const ScreenImage& screenImage,
        const cv::Rect& detectionArea,
        const std::string& recognitionModelId
) {
    // Get the region of interest within the screen image and convert to RGB
    cv::Mat screenCrop = screenImage.cropColor(detectionArea);
    cv::Mat rgbScreenCrop;
    cv::cvtColor(screenCrop, rgbScreenCrop, cv::COLOR_RGBA2RGB);
    if (rgbScreenCrop.empty()) {
        LOGE("TextMatcher", "Can't get rgb screen crop");
        return {};
    }

    // Find all regions containing text within the screen crop
    auto detectorResults = textLocator->detectText(rgbScreenCrop);

    // Recognize the text in the regions detected
    return textRecognizer->recognizeText(recognitionModelId, detectorResults);
}

float TextMatcher::bestSubstringSimilarity(const std::string& recognized, const std::string& target, float minSimilarity) {
    if (recognized.empty() || target.empty()) return 0.f;

    const auto recognizedPoints = toCodePoints(recognized);
    const auto targetPoints = toCodePoints(target);
    if (recognizedPoints.find(targetPoints) != std::u32string::npos) return 1.f;

    const int targetLen = static_cast<int>(targetPoints.size());
    const int recognizedLen = static_cast<int>(recognizedPoints.size());

    // Fast path
    if (recognizedLen <= targetLen + 2) return similarity(recognizedPoints, targetPoints, minSimilarity);

    // Allow small OCR insertions/deletions
    float bestScore = 0.f;
    const int minWindow = std::max(1, targetLen - 2);
    const int maxWindow = std::min(recognizedLen, targetLen + 4);

    std::u32string window;
    for (int windowSize = minWindow; windowSize <= maxWindow; ++windowSize) {
        for (int start = 0; start <= recognizedLen - windowSize; ++start) {
            window.assign(recognizedPoints, start, windowSize);

            float score = similarity(window, targetPoints, minSimilarity);
            if (score > bestScore) {
                bestScore = score;

                // Early success exit
                if (bestScore >= 0.95f) return bestScore;
            }
        }
    }

    return bestScore;
}

float TextMatcher::similarity(const std::u32string &recognized, const std::u32string &target, float minSimilarity) {
    if (recognized.empty() || target.empty()) return 0.f;

    const int n = static_cast<int>(recognized.size());
    const int m = static_cast<int>(target.size());
    const int maxLen = std::max(n, m);

    // Early impossible length check
    int maxAllowedDistance = static_cast<int>((1.f - minSimilarity) * maxLen);
    if (std::abs(n - m) > maxAllowedDistance) return 0.f;

    comparisonPrevPrevRow.resize(m + 1);
    comparisonPrevRow.resize(m + 1);
    comparisonCurrRow.resize(m + 1);

    for (int j = 0; j <= m; ++j) comparisonPrevRow[j] = j;

    for (int i = 1; i <= n; ++i) {
        comparisonCurrRow[0] = i;
        int rowMin = comparisonCurrRow[0];

        char32_t ca = normalizeChar(recognized[i - 1]);
        for (int j = 1; j <= m; ++j) {
            char32_t cb = normalizeChar(target[j - 1]);

            int cost = (ca == cb) ? 0 : 1;

            int deletion = comparisonPrevRow[j] + 1;
            int insertion = comparisonCurrRow[j - 1] + 1;
            int substitution = comparisonPrevRow[j - 1] + cost;
            int value = std::min({ deletion, insertion, substitution });

            // Damerau transposition
            if (i > 1 && j > 1 && ca == normalizeChar(target[j - 2]) && normalizeChar(recognized[i - 2]) == cb) {
                value = std::min(value, comparisonPrevPrevRow[j - 2] + 1);
            }

            comparisonCurrRow[j] = value;
            rowMin = std::min(rowMin, value);
        }

        // Early exit
        if (rowMin > maxAllowedDistance) return 0.f;

        std::swap(comparisonPrevPrevRow, comparisonPrevRow);
        std::swap(comparisonPrevRow, comparisonCurrRow);
    }

    int distance = comparisonPrevRow[m];

    float score = 1.f - (float)distance / (float)maxLen;
    return std::max(0.f, score);
}

char32_t TextMatcher::normalizeChar(char32_t c) {
    if (c >= U'A' && c <= U'Z') return c + 32;
    return c;
}

bool TextMatcher::isNumber(const std::string& text) {
    if (text.empty()) return false;

    bool hasDigit = false;
    for (char c : text) {
        if (std::isdigit(static_cast<unsigned char>(c))) {
            hasDigit = true;
            continue;
        }

        if (c == '.' || c == ',' || c == ' ' || c == '-' || c == '+') continue;

        return false;
    }

    return hasDigit;
}

double TextMatcher::stringToDouble(const std::string& text, NumberFormat format) {
    std::string s = text;
    // Remove spaces
    s.erase(std::remove(s.begin(), s.end(), ' '), s.end());

    char decimalSep;
    char thousandsSep;

    if (format == NumberFormat::DOT_DECIMAL) {
        decimalSep   = '.';
        thousandsSep = ',';
    } else if (format == NumberFormat::COMMA_DECIMAL) {
        decimalSep   = ',';
        thousandsSep = '.';
    } else {
        // AUTO: infer from structure.
        // If both separators are present, the last one is the decimal separator.
        size_t lastDot   = s.rfind('.');
        size_t lastComma = s.rfind(',');

        if (lastDot != std::string::npos && lastComma != std::string::npos) {
            decimalSep   = (lastDot > lastComma) ? '.' : ',';
            thousandsSep = (lastDot > lastComma) ? ',' : '.';
        } else if (lastComma != std::string::npos) {
            // Only a comma: thousands separator if exactly 3 digits follow it, else decimal.
            size_t digitsAfterComma = s.size() - lastComma - 1;
            if (digitsAfterComma == 3 && lastComma > 0) {
                decimalSep   = '.';
                thousandsSep = ',';
            } else {
                decimalSep   = ',';
                thousandsSep = '.';
            }
        } else {
            // Only a dot (or none): treat dot as decimal separator.
            decimalSep   = '.';
            thousandsSep = ',';
        }
    }

    // Strip thousands separators, then normalise decimal separator to '.'.
    std::string sanitized;
    sanitized.reserve(s.size());
    for (char c : s) {
        if (c == thousandsSep) continue;
        sanitized += (c == decimalSep) ? '.' : c;
    }

    try {
        return std::stod(sanitized);
    } catch (...) {
        return std::numeric_limits<double>::lowest();
    }
}
