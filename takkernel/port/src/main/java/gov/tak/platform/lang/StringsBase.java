package gov.tak.platform.lang;

abstract class StringsBase
{
    public static boolean isBlank(String s) {
        final int length = s.length();
        for(int i = 0; i < length; i++)
            if(!Character.isWhitespace(s.charAt(i)))
                return false;
        return true;
    }
}
