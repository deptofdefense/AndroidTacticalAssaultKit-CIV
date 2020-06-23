package transapps.maps.plugin.tool;

import android.os.Bundle;

/**
 * Allows a tool to answer the questions, "Which tools may become
 * active while my tool is active" and "Which tools may remain active
 * when my tool becomes active".
 * 
 * @author mriley
 */
public abstract class ActivationRule {
    
    /**
     * The output of processing a rule
     * 
     * @author mriley
     */
    public enum Result {
        /**
         * If the result of a rule is that a tool does not exclude
         * another tool, this should be returned.
         */
        ACTIVATE,
        /**
         * If the result of the rule execution is that a
         * tool excludes another tool and should
         * not be activated, this should be returned
         */
        DONT_ACTIVATE,
        /**
         * If the result of the rule execution is that a
         * tool excludes another tool and the
         * other tool should be made inactive, this
         * should be returned
         */
        ACTIVATE_CLOSE_ACTIVE
    }

    /**
     * Used by the {@link TypeExclusion} rule to determine
     * exclusivity based on type
     * 
     * @author mriley
     */
    public enum Type {
        /**
         * This group is for full screen tools.
         */
        FULL_SCREEN,
        /**
         * This group is for popup tools.  Generally,
         * popups aren't excluded by other types
         */
        POPUP,
        /**
         * This group is for callout tools.
         */
        CALLOUT,
        /**
         * This is a tool that is in another activity
         */
        ACTIVITY
    }
    
    /**
     * Rule that makes a tool not exclusive to others
     * 
     * @author mriley
     */
    public static class NoExclusion extends ActivationRule {
        @Override
        public Result processActivate(ToolDescriptor toolDescriptor, Bundle activationArgs) {
            return Result.ACTIVATE;
        }
        @Override
        public Result processActive(ToolDescriptor toolDescriptor, Bundle activationArgs) {
            return Result.ACTIVATE;
        }
    }
    
    /**
     * Rule that that is predetermined based on a single {@link Result}
     * 
     * @author mriley
     */
    public static class SingleActionExclusion extends ActivationRule {
        private Result action;
        
        public SingleActionExclusion(Result action) {
            this.action = action;            
        }
        
        @Override
        public Result processActivate(ToolDescriptor toolDescriptor, Bundle activationArgs) {
            return action;
        }
        
        @Override
        public Result processActive(ToolDescriptor toolDescriptor, Bundle activationArgs) {
            return Result.ACTIVATE;
        }
    }
    
    /**
     * Rule that determines exclusivity based on {@link Type}.  For simplicity I've
     * made {@link Type#POPUP}s are generally not exclusive to anything.  This
     * makes things easier.  If this turns out to be wrong, we'll have to come up
     * with something better.
     * 
     * @author mriley
     */
    public static class TypeExclusion extends ActivationRule {

        private Type type;
        private Result matchAction;
        
        public TypeExclusion( Type type, Result  matchAction ) {
            this.type = type;
            this.matchAction = matchAction;
        }
        
        public Type getType() {
            return type;
        }
        
        public Result getMatchAction() {
            return matchAction;
        }
        
        @Override
        public Result processActivate(ToolDescriptor toolDescriptor, Bundle activationArgs) {
            ActivationRule exclusion = toolDescriptor.getTool().getActivationRule();
            if( exclusion instanceof TypeExclusion ) {
                Type otherType = ((TypeExclusion) exclusion).type;
                if( otherType != Type.POPUP && otherType == type ) {
                    return matchAction;                            
                } else {
                    return Result.ACTIVATE;
                }
            }
            return Result.ACTIVATE_CLOSE_ACTIVE;
        }
        
        @Override
        public Result processActive(ToolDescriptor toolDescriptor, Bundle activationArgs) {
            return Result.ACTIVATE;
        }
    }
    
    
    /**
     * Process this rule against the {@link ToolDescriptor} that is being activated.
     * In this case, the {@link Tool} that this rule represents is active and the
     * {@link ToolDescriptor} passed in is being activated.  The question being
     * answered here is, "should I allow the tool to become active and should I remain
     * active?".  Generally, you will want to filter out most tools here.
     * 
     * @param toolDescriptor
     * @param activationArgs
     * @return The action
     */
    public abstract Result processActivate( ToolDescriptor toolDescriptor, Bundle activationArgs );
    
    /**
     * Process this rule against the {@link ToolDescriptor} that is active.
     * In this case, the {@link Tool} that this rule represents is being activated
     * and the currently active tool's {@link ActivationRule}'s have already
     * been processed.  The question being answered here is, "should I become
     * active and should the tool remain active?".  Generally, you will want to
     * all most things to remain active.
     * 
     * @param toolDescriptor
     * @param activationArgs
     * @return
     */
    public abstract Result processActive( ToolDescriptor toolDescriptor, Bundle activationArgs );
}
