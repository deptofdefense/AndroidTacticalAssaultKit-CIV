
package com.atakmap.app;

import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Interface for ATAK Crash listeners
 *
 * 
 */
public interface CrashListener {

    class CrashLogSection {
        String label;
        String content;

        public CrashLogSection(String label, String content) {
            this.label = label;
            this.content = content;
        }

        public boolean isValid() {
            return !FileSystemUtils.isEmpty(label)
                    && !FileSystemUtils.isEmpty(content);
        }

        public String toString() {
            return toJson();
        }

        String toJson() {
            return "\"section\":\"" + label + "\",\"records\":[" + content
                    + "]";
        }
    }

    /**
     * Take action upon crash, optionally return section for inclusion in crash log
     * @return the crash log section associated with the crash event.
     */
    CrashLogSection onCrash();
}
