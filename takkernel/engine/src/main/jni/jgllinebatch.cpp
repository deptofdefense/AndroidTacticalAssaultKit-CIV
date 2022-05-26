#include "common.h"
#include "jgllinebatch.h"

#include <cstring>

#define LINES_VERTEX_SIZE \
    com_atakmap_opengl_GLLineBatch_LINES_VERTEX_SIZE

namespace {

void flush(JNIEnv *env, jobject jlineBatch, jobject jlinesBuffer, jint *linesBufferPos, jint *linesBufferLimit)
{
    env->CallVoidMethod(jlineBatch, glLineBatch_flush);

    *linesBufferPos = nioBuffer_position->get(env, jlinesBuffer);
    *linesBufferLimit = nioBuffer_limit->get(env, jlinesBuffer);

}

};

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLLineBatch_addLineNative
  (JNIEnv *env, jobject jthis, jobject jlinesBuffer, jint linesBufferPos, jint linesBufferLimit, jfloat x0, jfloat y0, jfloat x1, jfloat y1, jfloat r, jfloat g, jfloat b, jfloat a)
{
    const unsigned char red = (unsigned char)(r*255.0f);
    const unsigned char green = (unsigned char)(g*255.0f);
    const unsigned char blue = (unsigned char)(b*255.0f);
    const unsigned char alpha = (unsigned char)(a*255.0f);

    unsigned char color[4];
    color[0] = (unsigned char)(r*255.0f);
    color[1] = (unsigned char)(g*255.0f);
    color[2] = (unsigned char)(b*255.0f);
    color[3] = (unsigned char)(a*255.0f);

    jbyte *linesBuffer = GET_BUFFER_POINTER(jbyte, jlinesBuffer);
    jfloat *pLinesBuffer = reinterpret_cast<jfloat *>(linesBuffer+linesBufferPos);

    (*pLinesBuffer++) = x0;
    (*pLinesBuffer++) = y0;
	memcpy(pLinesBuffer, color, 4);
	pLinesBuffer ++;
	linesBufferPos += LINES_VERTEX_SIZE;

    (*pLinesBuffer++) = x1;
    (*pLinesBuffer++) = y1;
	memcpy(pLinesBuffer, color, 4);
	pLinesBuffer ++;
	linesBufferPos += LINES_VERTEX_SIZE;

	nioBuffer_position->set(env, jlinesBuffer, linesBufferPos);
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLLineBatch_addLinesNative
  (JNIEnv *env, jobject jthis, jobject jlinesBuffer, jint linesBufferPos, jint linesBufferLimit, jobject jlines, jint linesPos, jint linesRemaining, jfloat r, jfloat g, jfloat b, jfloat a)
{
    const int numSegments = linesRemaining / 4;
    int linesIdx = linesPos;

    const unsigned char red = (unsigned char)(r*255.0f);
    const unsigned char green = (unsigned char)(g*255.0f);
    const unsigned char blue = (unsigned char)(b*255.0f);
    const unsigned char alpha = (unsigned char)(a*255.0f);

    unsigned char color[4];
    color[0] = (unsigned char)(r*255.0f);
    color[1] = (unsigned char)(g*255.0f);
    color[2] = (unsigned char)(b*255.0f);
    color[3] = (unsigned char)(a*255.0f);

    jbyte *linesBuffer = GET_BUFFER_POINTER(jbyte, jlinesBuffer);
    jfloat *lines = GET_BUFFER_POINTER(jfloat, jlines);

    jbyte *pLinesBuffer = (linesBuffer+linesBufferPos);
    for(int i = 0; i < numSegments; i++) {
        if((linesBufferLimit-linesBufferPos) < (LINES_VERTEX_SIZE*2)) {
            flush(env, jthis, jlinesBuffer, &linesBufferPos, &linesBufferLimit);
            pLinesBuffer = (linesBuffer+linesBufferPos);
        }

        memcpy(pLinesBuffer, lines, sizeof(jfloat)*2);
        pLinesBuffer += 8;
        lines += 2;
        memcpy(pLinesBuffer, color, 4);
        pLinesBuffer += 4;
        linesBufferPos += LINES_VERTEX_SIZE;

        memcpy(pLinesBuffer, lines, sizeof(jfloat)*2);
        pLinesBuffer += 8;
        lines += 2;
        memcpy(pLinesBuffer, color, 4);
        pLinesBuffer += 4;
        linesBufferPos += LINES_VERTEX_SIZE;
    }

    nioBuffer_position->set(env, jlinesBuffer, linesBufferPos);
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLLineBatch_addLinestripNative
  (JNIEnv *env, jobject jthis, jobject jlinesBuffer, jint linesBufferPos, jint linesBufferLimit, jobject jlinestrip, jint linestripPos, jint linestripRemaining, jfloat r, jfloat g, jfloat b, jfloat a)
{
    const int numSegments = (linestripRemaining / 2)-1;
    int linestripX = linestripPos;
    int linestripY = linestripX+1;

    const unsigned char red = (unsigned char)(r*255.0f);
    const unsigned char green = (unsigned char)(g*255.0f);
    const unsigned char blue = (unsigned char)(b*255.0f);
    const unsigned char alpha = (unsigned char)(a*255.0f);

    unsigned char color[4];
    color[0] = (unsigned char)(r*255.0f);
    color[1] = (unsigned char)(g*255.0f);
    color[2] = (unsigned char)(b*255.0f);
    color[3] = (unsigned char)(a*255.0f);

    jbyte *linesBuffer = GET_BUFFER_POINTER(jbyte, jlinesBuffer);
    jfloat *linestrip = GET_BUFFER_POINTER(jfloat, jlinestrip);

    jbyte *pLinesBuffer = (linesBuffer+linesBufferPos);

    for(int i = 0; i < numSegments; i++) {
        if((linesBufferLimit-linesBufferPos) < (LINES_VERTEX_SIZE*2)) {
            flush(env, jthis, jlinesBuffer, &linesBufferPos, &linesBufferLimit);
            pLinesBuffer = (linesBuffer+linesBufferPos);
        }

        memcpy(pLinesBuffer, linestrip, sizeof(jfloat)*2);
        pLinesBuffer += 8;
        memcpy(pLinesBuffer, color, 4);
        pLinesBuffer += 4;
        linesBufferPos += LINES_VERTEX_SIZE;

        memcpy(pLinesBuffer, linestrip+2, sizeof(jfloat)*2);
        pLinesBuffer += 8;
        linestrip += 2;
        memcpy(pLinesBuffer, color, 4);
        pLinesBuffer += 4;
        linesBufferPos += LINES_VERTEX_SIZE;
    }

    nioBuffer_position->set(env, jlinesBuffer, linesBufferPos);
}
