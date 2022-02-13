package com.atakmap.map.layer.model.opengl;

import com.atakmap.android.maps.tilesets.graphics.GLPendingTexture;
import com.atakmap.map.layer.model.Material;
import com.atakmap.opengl.GLResolvable;
import com.atakmap.opengl.GLTexture;

public final class GLMaterial implements GLResolvable {

    private Material subject;
    private GLTexture texture;
    private GLPendingTexture pendingTexture;
    private int width;
    private int height;

    public GLMaterial(Material subject, GLTexture texture) {
        this.subject = subject;
        this.texture = texture;
    }

    public GLMaterial(Material subject, GLPendingTexture pendingTexture) {
        this.subject = subject;
        this.pendingTexture = pendingTexture;
    }

    public Material getSubject() {
        return subject;
    }

    public GLTexture getTexture() {
        synchronized (this) {
            if (this.pendingTexture != null && !this.pendingTexture.isPending()) {
                this.texture = this.pendingTexture.getTexture();
                if (this.texture != null) {
                    this.width = this.pendingTexture.getWidth();
                    this.height = this.pendingTexture.getHeight();
                    this.pendingTexture = null;
                }
            }
        }
        return texture;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public boolean isTextured() {
        return this.subject.getTextureUri() != null &&
                !this.subject.getTextureUri().isEmpty();
    }

    void dispose() {
        if (texture != null) {
            this.texture.release();
            this.texture = null;
        }
        this.pendingTexture = null;
    }

    @Override
    public State getState() {
        synchronized (this) {
            if (pendingTexture != null) {
                switch (pendingTexture.getState()) {
                    case GLPendingTexture.STATE_UNRESOLVED:
                    case GLPendingTexture.STATE_CANCELED:
                        return State.UNRESOLVABLE;
                    case GLPendingTexture.STATE_PENDING:
                        return State.RESOLVING;
                    case GLPendingTexture.STATE_RESOLVED:
                        this.getTexture();
                        break;
                }
            }
            if (this.texture != null) {
                return State.RESOLVED;
            }
        }
        return State.UNRESOLVABLE;
    }

    @Override
    public void suspend() {

    }

    @Override
    public void resume() {

    }
}
