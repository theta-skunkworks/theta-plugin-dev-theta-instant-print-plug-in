#include <jni.h>
#include <string>
#include <cstdint>
#include <cassert>
#include <memory>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

extern "C"
{
const uint8_t THRESHOLD = 127;
const uint8_t WHITE = 255;
const uint8_t BLACK = 0;

int update_pixel(uint8_t *, int, int, int, int);
void update_neighboring_pixel(uint8_t *, int, int, int, int, int);
void gray2mono(const uint8_t *, uint8_t *, int, int);
void mono2printable(const uint8_t *, uint8_t *, int, int);

JNIEXPORT jbyteArray JNICALL Java_skunkworks_instantprint_ImgConv_rgba2printable(
        JNIEnv *env,
        jclass type,
        jbyteArray src,
        jint width,
        jint height
) {
    jbyte *p_src = env->GetByteArrayElements(src, nullptr);

    cv::Mat m_src(height, width, CV_8UC4, (u_char *) p_src);
    cv::Mat m_gray(height, width, CV_8U);
    cv::Mat m_mono(height, width, CV_8U);
    auto data = new uint8_t[width / 8 * height];

    cv::cvtColor(m_src, m_gray, cv::COLOR_RGBA2GRAY);
    gray2mono(m_gray.data, m_mono.data, width, height);
    mono2printable(m_mono.data, data, width, height);

    // Allocate new byte array for return value.
    jbyteArray dst = env->NewByteArray(width / 8 * height);
    env->SetByteArrayRegion(dst, 0, width / 8 * height, (jbyte *) data);

    // release
    env->ReleaseByteArrayElements(src, p_src, 0);

    delete[] data;

    return dst;
}

int update_pixel(uint8_t *img, int width, int height, int j, int i) {
    assert(0 <= j && j < width && 0 <= i && i < height);

    const uint8_t old_value = img[i * width + j];
    const uint8_t new_value = old_value > THRESHOLD ? WHITE : BLACK;
    img[i * width + j] = new_value;
    return (int) old_value - (int) new_value;
}

void update_neighboring_pixel(uint8_t *img, int width, int height, int j, int i, int a) {
    if (0 <= j && j < width && 0 <= i && i < height) {
        img[i * width + j] += a;
    }
}

// Floyd–Steinberg dithering
void gray2mono(const uint8_t *src, uint8_t *dst, int width, int height) {
    std::memcpy(dst, src, width * height);

    for (int i = 0; i < height; i++) {
        if (i % 2 == 0) {
            for (int j = 0; j < width; j++) {
                // +----+----+----+
                // |    |  * | f1 |
                // +----+----+----+
                // | f2 | f3 | f4 |
                // +----+----+----+
                const int err = update_pixel(dst, width, height, j, i);
                update_neighboring_pixel(dst, width, height, j + 1, i, 7.0 / 16.0 * err);     // f1
                update_neighboring_pixel(dst, width, height, j - 1, i + 1, 3.0 / 16.0 * err); // f2
                update_neighboring_pixel(dst, width, height, j, i + 1, 5.0 / 16.0 * err);     // f3
                update_neighboring_pixel(dst, width, height, j + 1, i + 1, 1.0 / 16.0 * err); // f4
            }
        } else {
            for (int j = width - 1; j >= 0; j--) {
                // +----+----+----+
                // | f1 |  * |    |
                // +----+----+----+
                // | f4 | f3 | f2 |
                // +----+----+----+
                const int err = update_pixel(dst, width, height, j, i);
                update_neighboring_pixel(dst, width, height, j - 1, i, 7.0 / 16.0 * err);     // f1
                update_neighboring_pixel(dst, width, height, j + 1, i + 1, 3.0 / 16.0 * err); // f2
                update_neighboring_pixel(dst, width, height, j, i + 1, 5.0 / 16.0 * err);     // f3
                update_neighboring_pixel(dst, width, height, j - 1, i + 1, 1.0 / 16.0 * err); // f4
            }
        }
    }
}

void mono2printable(const uint8_t *src, uint8_t *dst, int width, int height) {
    assert(width % 8 == 0);

    size_t pt = 0;
    for (int i = 0; i < height; i++) {
        for (int j = 0; j < width; j += 8) {
            const size_t offset = static_cast<const size_t>(i * width + j);
            const uint8_t pixels = (src[offset + 0] & (uint8_t) 0x80) |
                                   (src[offset + 1] & (uint8_t) 0x40) |
                                   (src[offset + 2] & (uint8_t) 0x20) |
                                   (src[offset + 3] & (uint8_t) 0x10) |
                                   (src[offset + 4] & (uint8_t) 0x08) |
                                   (src[offset + 5] & (uint8_t) 0x04) |
                                   (src[offset + 6] & (uint8_t) 0x02) |
                                   (src[offset + 7] & (uint8_t) 0x01);
            dst[pt++] = ~pixels;
        }
    }
}

} // extern "C"
