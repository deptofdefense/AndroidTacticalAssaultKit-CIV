package com.atakmap.util;

public class WildCard { 

   /**
    * Given a wildcard string, produce the appropriate regular expression.
    * @param s the string possibly containing one or more % as the wildcard character.
    * @return the string properly escaped so that it can be used as a regular expression or null
    * if the original string passed in was null.
    */
   public static String wildcardAsRegex(final String s) {
        return wildcardAsRegex(s, '%');
   }


   /**
    * Given a wildcard string, produce the appropriate regular expression.
    * @param s the string 
    * @param wildcard defines the appropriate wildchard character
    * @return the string properly escaped so that it can be used as a regular expression or null
    * if the original string passed in was null.
    */
   public static String wildcardAsRegex(final String s, final char wildcard) {
        if (s == null)
           return null;

        final StringBuilder retval = new StringBuilder();
        char c = ' ';

        char prev;

        for (int i = 0; i < s.length(); i++) {
            prev = c;
            c = s.charAt(i);
            if (prev == wildcard && c == wildcard) {
                // no op
            } else if (i + 1 < s.length() && c == '\\' && s.charAt(i+1) == wildcard) {
               retval.append(c);
               retval.append(s.charAt(i+1));
               // move the index forward
               i++;
            } else if (c == wildcard) { 
               retval.append(".*");
            } else {
                switch (c) {
                    case '*':
                    case '\\':
                    case '[':
                    case ']':
                    case '^':
                    case '$':
                    case '?':
                    case '.':
                    case '{':
                    case '}':
                    case '+':
                    case '|':
                    case '(':
                    case ')':
                    case '-':
                    case ':':
                    case '!':
                    case '&':
                        retval.append('\\');
                        // intended to fall through to default
                    default:
                        retval.append(c);
                }
            }
        }
        return retval.toString();
    }

     

}
