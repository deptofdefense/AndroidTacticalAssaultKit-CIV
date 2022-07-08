
package com.atakmap.android.fires.bridge;

/**
 * Bridge component in support of the the flavor subsystem.
 * Since both 5-line and 9-line contain separate
 * Remarks Views, this class holds the strings
 * for dealing with the remarks for both
 */
public class RemarksConstants {

    //Visibility
    final static public String SHOW_AIM = "show_aim";
    final static public String SHOW_FAH = "show_fah";
    final static public String SHOW_GTL = "show_gtl";
    final static public String SHOW_ACA = "show_aca";
    final static public String SHOW_TXT = "show_txt";
    final static public String SHOW_TTT = "show_ttt";
    final static public String SHOW_STAT = "show_stat";
    final static public String SHOW_CALLS = "show_calls";

    //FAH
    final static public String FAH_DISPLAY = "remarks_fahDisplayEnabled";
    final static public String HAS_FAH = "hasFAH";
    final static public String FAH_OFFSET = "nineline_remarks_fahOffsetAngle";
    final static public String FAH_WIDTH = "nineline_remarks_fahWidth";
    final static public String FAH_HAS_RECIPROCAL = "fah_has_reciprocal";

    //STAT
    final static public String HAS_STAT = "hasSTAT";
    final static public String STAT_LIST = "stat_list";

    //ACA
    final static public String LINE_ACA_ITEM = "line_aca_item";
    final static public String LINE_ACA_DIR = "line_aca_dir";
    final static public String LINE_ACA_ALTLOW = "line_aca_altlow";
    final static public String LINE_ACA_ALTHIGH = "line_aca_althigh";
    final static public String LINE_ACA_USE_ALT = "line_aca_useAlt";
    final static public String LINE_ACA_USE_LAT = "line_aca_useLat";
    final static public String LINE_ACA_USE_CUSTOM = "line_aca_useCustom";
    final static public String LINE_ACA_CUSTOM_LAT = "line_aca_customLat";

    //GTL
    final static public String LINE_GTL_MAXORD = "line_gtl_maxord";
    final static public String LINE_GTL_UID = "line_gtl_uid";
    final static public String LINE_GTL_VIS = "line_gtl_vis";

    //TTT
    final static public String LINE_TTT = "line_ttt";
    final static public String LINE_TTT_OFFSET = "line_ttt_offset";

    //Free Text
    final static public String FREETEXT_REMARKS = "remarks";

    //WPN
    final static public String WPN_DISPLAY = "remarks_munitionDisplayEnabled";
    final static public String HAS_WPN = "hasMunition";
    final static public String WPN_INTERVAL = "munitionTiming";
    final static public String WPN_COUNT = "munitionNumber";
    final static public String WPN_FUZING = "munitionFuzing";
    final static public String SUMMARY = "remarks_summary";

    //Danger Close Initials
    final static public String LINE_DCI = "nineline_line_dci";
    final static public String DANGERCLOSE_INITIALS = "dcCode";

    //Advisory Calls
    final static public String HAS_PUSHING = "has_pushing";
    final static public String HAS_IN = "has_in";
    final static public String HAS_VISUAL = "has_visual";
    final static public String HAS_TALLY = "has_tally";
    final static public String HAS_WEAPONS_AWAY = "has_weapons_away";

    //LASER
    final static public String LINE7_CODE = "_line_7_code";
    final static public String LINE7_DESIGNATOR = "_line_7_designator";
    final static public String LINE_7 = "_line_7";
    final static public String LINE7_OTHER = "_line_7_other";
    final static public String LINE7_NAME = "_line_7_name";
    final static public String LINE7_SAFETYZONE_ENABLED = "_line_7_safetyzoneenabled";
    final static public String DESIGNATOR_UID = "designatorUID";
}
