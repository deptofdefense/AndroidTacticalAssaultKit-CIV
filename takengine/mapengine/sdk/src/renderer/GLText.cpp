#include "GLText.h"
#include "GLES20FixedPipeline.h"

#include <cfloat>

#include "math/Utils.h"
#include "RendererUtils.h"
#include "core/AtakMapView.h"


namespace atakmap {
    namespace renderer {


        /**********************************************************************/
        // Private implementation statics

        namespace {
            const int CHAR_START = 32; // first character (ASCII Code)
            const int CHAR_END = 126; // last character (ASCII Code)
            int CHAR_BATCH_SIZE = 80; // number of characters to render per batch

            GLRenderBatch *SPRITE_BATCH = nullptr;
        }


        /**********************************************************************/
        // Factory method

        std::map<intptr_t, GLText *> GLText::glTextCache;
        float *GLText::trianglesTexCoords;
        float *GLText::trianglesVerts;
        short *GLText::trianglesIndices;

        GLText *GLText::getInstance(TextFormat *textFormat)
        {
            if (!SPRITE_BATCH)
                SPRITE_BATCH = new GLRenderBatch(CHAR_BATCH_SIZE);

            int textSize = textFormat->getFontSize();

            // performs safe volatile double checked locking
            auto key = (intptr_t)textFormat;
            GLText *customGLText = nullptr;
            auto entry = glTextCache.find(key);
            if (entry != glTextCache.end()) {
                customGLText = entry->second;
            } else {
                customGLText = new GLText(textFormat, textSize * core::AtakMapView::DENSITY);
                glTextCache.insert(std::pair<intptr_t, GLText *>(key, customGLText));
            }

            if (trianglesIndices == nullptr) {
                trianglesVerts = new float[CHAR_BATCH_SIZE * 4 * 2];

                trianglesTexCoords = new float[CHAR_BATCH_SIZE * 4 * 2];

                trianglesIndices = new short[CHAR_BATCH_SIZE * 6];
            }

            return customGLText;

        }


        /**********************************************************************/
        // Constructor/destructor (private)


        GLText::GLText(TextFormat *textFormat, float densityAdjustedTextSize) :
            scratchCharBounds(0, 0, 0, 0)
        {
            this->textFormat = textFormat;

            glyphAtlas = new GLTextureAtlas(256, true);

            charMaxHeight = textFormat->getCharHeight();

            int numCommonChars = CHAR_END - CHAR_START + 1;
            commonCharTexId = new int[numCommonChars];
            memset(commonCharTexId, 0, sizeof(int) * numCommonChars);
            commonCharUV = new float[numCommonChars * 4];
            commonCharWidth = new float[numCommonChars];
        }

        GLText::~GLText()
        {
            delete[] commonCharTexId;
            delete[] commonCharUV;
            delete[] commonCharWidth;

            delete glyphAtlas;
        }



        /**********************************************************************/
        // Public Draw methods

        void GLText::drawSplitString(const char *text, int *colors, size_t colorsCount)
        {
            SPRITE_BATCH->begin();
            batchSplitString(SPRITE_BATCH, text, 0.0f, 0.0f, colors, colorsCount);
            SPRITE_BATCH->end();
        }

        void GLText::drawSplitString(const char *text, float r, float g, float b, float a)
        {
            SPRITE_BATCH->begin();
            batchSplitString(SPRITE_BATCH, text, 0.0f, 0.0f, r, g, b, a);
            SPRITE_BATCH->end();
        }

        void GLText::draw(const char *text, float r, float g, float b, float a)
        {
            SPRITE_BATCH->begin();
            batch(SPRITE_BATCH, text, 0.0f, 0.0f, r, g, b, a);
            SPRITE_BATCH->end();
        }

        void GLText::draw(const char *text, float r, float g, float b, float a, float scissorX0,
                          float scissorX1)
        {
            SPRITE_BATCH->begin();
            batchImpl(SPRITE_BATCH, 0.0f, 0.0f, text, 0, strlen(text), r, g, b, a, scissorX0,
                      scissorX1);
            SPRITE_BATCH->end();
        }


        /**********************************************************************/
        // Public Batch methods


        void GLText::batchSplitString(GLRenderBatch *batch, const char *text, float x, float y, int *colors, size_t colorsCount)
        {
            int lineNum = 0;
            size_t length = strlen(text);
            int lineStart = 0;
            int lineChars = 0;
            int c;
            for (std::size_t i = 0; i < length; i++) {
                if (text[i] == '\n') {
                    if (lineChars > 0) {
                        if (colors != nullptr)
                            c = colors[math::clamp<int>(lineNum, 0, static_cast<int>(colorsCount) - 1)];
                        else
                            // White
                            c = Utils::WHITE;

                        batchImpl(batch,
                                       x + 0,
                                       y + -textFormat->getBaselineSpacing() * (lineNum + 1),
                                       text,
                                       lineStart,
                                       lineChars,
                                       Utils::colorExtract(c, Utils::RED) / 255.0f,
                                       Utils::colorExtract(c, Utils::GREEN) / 255.0f,
                                       Utils::colorExtract(c, Utils::BLUE) / 255.0f,
                                       Utils::colorExtract(c, Utils::ALPHA) / 255.0f);
                    }

                    lineStart = static_cast<int>(i + 1);
                    lineChars = 0;
                    lineNum++;
                } else {
                    lineChars++;
                }
            }

            if (lineChars > 0) {
                if (colors != nullptr)
                    c = colors[math::clamp<int>(lineNum, 0, static_cast<int>(colorsCount) - 1)];
                else
                    c = Utils::WHITE;

                batchImpl(batch,
                               x + 0,
                               y + -textFormat->getBaselineSpacing() * (lineNum + 1),
                               text,
                               lineStart,
                               lineChars,
                               Utils::colorExtract(c, Utils::RED) / 255.0f,
                               Utils::colorExtract(c, Utils::GREEN) / 255.0f,
                               Utils::colorExtract(c, Utils::BLUE) / 255.0f,
                               Utils::colorExtract(c, Utils::ALPHA) / 255.0f);
            }

        }

        void GLText::batchSplitString(GLRenderBatch *batch, const char *text, float x, float y, float r,
                                      float g, float b, float a)
        {
            int lineNum = 0;
            size_t length = strlen(text);
            int lineStart = 0;
            int lineChars = 0;
            for (std::size_t i = 0; i < length; i++) {
                if (text[i] == '\n') {
                    if (lineChars > 0) {
                        batchImpl(batch,
                                       x + 0.0f,
                                       y + -textFormat->getBaselineSpacing() * (lineNum + 1),
                                       text,
                                       lineStart,
                                       lineChars,
                                       r, g, b, a);
                    }

                    lineStart = static_cast<int>(i + 1);
                    lineChars = 0;
                    lineNum++;
                } else {
                    lineChars++;
                }
            }

            if (lineChars > 0) {
                batchImpl(batch,
                               x + 0.0f,
                               y + -textFormat->getBaselineSpacing() * (lineNum + 1),
                               text,
                               lineStart,
                               lineChars,
                               r, g, b, a);
            }
        }

        void GLText::batch(GLRenderBatch *batchp, const char *text, float x, float y, float r, float g,
                           float b, float a)
        {
            batch(batchp, text, x, y, r, g, b, a, 0.0f, FLT_MAX);
        }

        void GLText::batch(GLRenderBatch *batch, const char *text, float x, float y, float r, float g,
                           float b, float a, float scissorX0, float scissorX1)
        {
            batchImpl(batch, x, y, text, 0, strlen(text), r, g, b, a, scissorX0, scissorX1);
        //    batchImplDebug(batch, x, y, text, 0, strlen(text), r, g, b, a, scissorX0, scissorX1);
        }




        /**********************************************************************/
        // Other Public methods

        TextFormat *GLText::getTextFormat()
        {
            return textFormat;
        }
        int GLText::getLineCount(const char *text)
        {
            int numLines = 1;
            size_t len = strlen(text);
            for (size_t i = 0; i < len; i++)
                if (text[i] == '\n')
                    numLines++;
            return numLines;
        }





        /**********************************************************************/
        // Private implementation methods

        void GLText::batchImpl(GLRenderBatch *batch, float tx, float ty, const char *text, int off, int len,
                               float r, float g, float b, float a)
        {
            batchImpl(batch, tx, ty, text, off, len, r, g, b, a, 0.0f, FLT_MAX);
        }

        void GLText::batchImpl(GLRenderBatch *batch, float tx, float ty, const char *text, size_t off, size_t len,
                               float r, float g, float b, float a, float scissorX0, float scissorX1)
        {
            float fontPadX = 0;
            float fontPadY = 0;

            float cellWidth;
            //float cellHeight = textFormat->getCharHeight();// + (2*fontPadY);
            float cellHeight = textFormat->getStringHeight(text);// + (2*fontPadY);

            float charAdjX; // adjust start X
            float charAdjY = (cellHeight / 2.0f) - fontPadY; // adjust start Y
            charAdjY -= textFormat->getDescent() - fontPadY;

            float letterX, letterY;
            letterX = letterY = 0;

            int c;
            int64_t key;
            int texId;
            float charWidth;
            auto texSize = (float)glyphAtlas->getTextureSize();

            float u0;
            float v0;
            float u1;
            float v1;
            int trianglesTexId = 0;
            int numBufferedChars = 0;

            for (std::size_t i = 0; i < len; i++) { // for each character in string
                c = (int)text[i + off];

                // skip non-printable
                if (c < 32)
                    continue;

                if (c >= CHAR_START && c <= CHAR_END) {
                    c -= CHAR_START;
                    texId = commonCharTexId[c];
                    if (texId == 0) {
                        key = loadGlyph(text[i + off]);

                        texId = glyphAtlas->getTexId(key);

                        commonCharWidth[c] = (float)glyphAtlas->getImageWidth(key);
                        commonCharTexId[c] = texId;

                        glyphAtlas->getImageRect(key, true, &scratchCharBounds);
                        commonCharUV[c * 4] = scratchCharBounds.x;
                        commonCharUV[c * 4 + 1] = scratchCharBounds.y + scratchCharBounds.height;
                        commonCharUV[c * 4 + 2] = scratchCharBounds.x + scratchCharBounds.width;
                        commonCharUV[c * 4 + 3] = scratchCharBounds.y;
                    }

                    charWidth = commonCharWidth[c];

                    u0 = commonCharUV[c * 4];
                    v0 = commonCharUV[c * 4 + 1];
                    u1 = commonCharUV[c * 4 + 2];
                    v1 = commonCharUV[c * 4 + 3];
                } else {
                    char uri[2];
                    uri[0] = text[i + off];
                    uri[1] = '\0';
                    key = glyphAtlas->getTextureKey(std::string(uri));
                    if (key == 0L) // load the glyph
                        key = loadGlyph(uri[0]);

                    glyphAtlas->getImageRect(key, false, &scratchCharBounds);
                    charWidth = scratchCharBounds.width;
                    texId = glyphAtlas->getTexId(key);

                    u0 = scratchCharBounds.x / texSize;
                    v0 = (scratchCharBounds.y + scratchCharBounds.height) / texSize;
                    u1 = (scratchCharBounds.x + scratchCharBounds.width) / texSize;
                    v1 = scratchCharBounds.y / texSize;
                }

                cellWidth = charWidth;
                charAdjX = (cellWidth / 2.0f) - fontPadX;
                if ((letterX + charWidth) >= scissorX0 && letterX < scissorX1) {
                    float x0 = charAdjX + letterX - cellWidth / 2.0f;
                    float y0 = charAdjY + letterY - cellHeight / 2.0f;
                    float x1 = charAdjX + letterX + cellWidth / 2.0f;
                    float y1 = charAdjY + letterY + cellHeight / 2.0f;

                    // adjust the vertex and text coordinates to account for
                    // scissoring
                    if (letterX < scissorX0) {
                        u0 += (scissorX0 - x0) / texSize;
                        x0 = scissorX0;
                    }
                    if ((letterX + charWidth) > scissorX1) {
                        u1 -= (x1 - scissorX1) / texSize;
                        x1 = scissorX1;
                    }

                    // draw the character

                    if (trianglesTexId == 0) {
                        trianglesTexId = texId;
                    } else if (trianglesTexId != texId || numBufferedChars == CHAR_BATCH_SIZE) {
                        batch->addTrianglesSprite(trianglesVerts,
                                                 numBufferedChars * 8,
                                                 trianglesIndices,
                                                 numBufferedChars * 6,
                                                 trianglesTexCoords, trianglesTexId,
                                                 r, g, b, a);

                        trianglesTexId = texId;

                        numBufferedChars = 0;
                    }

                    bufferChar(trianglesVerts + (numBufferedChars << 3),
                               trianglesTexCoords + (numBufferedChars << 3),
                               trianglesIndices + ((6 * numBufferedChars)),
                               numBufferedChars,
                               tx + x0, ty + y0,
                               tx + x1, ty + y1,
                               u0, v0,
                               u1, v1);

                    numBufferedChars++;
                }

                // advance X position by scaled character width
#if 1
                letterX += charWidth;
#else
                letterX += getTextFormat()->getCharPositionWidth(text, i + off);//charWidth;
#endif
                if (letterX > scissorX1)
                    break;
            }

            if (numBufferedChars > 0) {
                // NOTE: position should always be at zero, we only ever modify
                //       limit
                batch->addTrianglesSprite(trianglesVerts,
                                          numBufferedChars * 8,
                                          trianglesIndices,
                                          numBufferedChars * 6,
                                          trianglesTexCoords, trianglesTexId,
                                          r, g, b, a);
            }
        }

        int64_t GLText::loadGlyph(const char c)
        {
            Bitmap b = textFormat->loadGlyph(c);
            char buf[2] = { c, '\0' };
            int64_t ret = glyphAtlas->addImage(std::string(buf), b);
            b.releaseData(b);
            return ret;
        }


        void GLText::bufferChar(float *texVerts, float *texCoords, short *texIndices, int n, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1)
        {
            texVerts[0] = x0; // UL
            texVerts[1] = y0;
            texVerts[2] = x1; // UR
            texVerts[3] = y0;
            texVerts[4] = x1; // LR
            texVerts[5] = y1;
            texVerts[6] = x0; // LL
            texVerts[7] = y1;

            texCoords[0] = u0; // UL
            texCoords[1] = v0;
            texCoords[2] = u1; // UR
            texCoords[3] = v0;
            texCoords[4] = u1; // LR
            texCoords[5] = v1;
            texCoords[6] = u0; // LL
            texCoords[7] = v1;

            texIndices[0] = n * 4 + 3; // LL
            texIndices[1] = n * 4;   // UL
            texIndices[2] = n * 4 + 2; // LR

            texIndices[3] = n * 4;   // UL
            texIndices[4] = n * 4 + 2; // LR
            texIndices[5] = n * 4 + 1; // UR

        }

    }
}
