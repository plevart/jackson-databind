package com.fasterxml.jackson.databind.cfg;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;

/**
 * @since 2.12
 */
public class CoercionConfigs
    implements java.io.Serializable
{
    private static final long serialVersionUID = 1L;

    private final static int TARGET_TYPE_COUNT = CoercionTargetType.values().length;

    /**
     * Global default for cases not explicitly covered
     */
    protected CoercionAction _defaultAction;

    /**
     * Global default setting for whether blank (all-white space) String is
     * accepted as "empty" (zero-length) for purposes of coercions.
     *<p>
     * Default value is {@code false}, meaning blank Strings are NOT considered
     * "empty" for coercion purposes.
     */
    protected boolean _acceptBlankAsEmpty;

    /**
     * Coercion definitions by logical type ({@link CoercionTargetType})
     */
    protected MutableCoercionConfig[] _perTypeCoercions;

    /**
     * Coercion definitions by physical type (Class).
     */
    protected Map<Class<?>, MutableCoercionConfig> _perClassCoercions;

    /*
    /**********************************************************************
    /* Life cycle
    /**********************************************************************
     */

    public CoercionConfigs() {
        this(CoercionAction.TryConvert, false, null, null);
    }

    protected CoercionConfigs(CoercionAction defaultAction,
            boolean acceptBlankAsEmpty,
            MutableCoercionConfig[] perTypeCoercions,
            Map<Class<?>, MutableCoercionConfig> perClassCoercions) {
        _defaultAction = defaultAction;
        _acceptBlankAsEmpty = acceptBlankAsEmpty;
        _perTypeCoercions = perTypeCoercions;
        _perClassCoercions = perClassCoercions;
    }

    /**
     * Method called to create a non-shared copy of configuration settings,
     * to be used by another {@link com.fasterxml.jackson.databind.ObjectMapper}
     * instance.
     *
     * @return A non-shared copy of configuration settings
     */
    public CoercionConfigs copy()
    {
        MutableCoercionConfig[] newPerType;
        if (_perTypeCoercions == null) {
            newPerType = null;
        } else {
            final int size = _perTypeCoercions.length;
            newPerType = new MutableCoercionConfig[size];
            for (int i = 0; i < size; ++i) {
                newPerType[i] = _copy(_perTypeCoercions[i]);
            }
        }
        Map<Class<?>, MutableCoercionConfig> newPerClass;
        if (_perClassCoercions == null) {
            newPerClass = null;
        } else {
            newPerClass = new HashMap<>();
            for (Map.Entry<Class<?>, MutableCoercionConfig> entry : _perClassCoercions.entrySet()) {
                newPerClass.put(entry.getKey(), entry.getValue().copy());
            }
        }
        return new CoercionConfigs(_defaultAction, _acceptBlankAsEmpty,
                newPerType, newPerClass);
    }

    private static MutableCoercionConfig _copy(MutableCoercionConfig src) {
        if (src == null) {
            return null;
        }
        return src.copy();
    }

    /*
    /**********************************************************************
    /* Mutators: global defaults
    /**********************************************************************
     */

    public void setAcceptBlankAsEmpty(boolean state) {
        _acceptBlankAsEmpty = state;
    }

    /*
    /**********************************************************************
    /* Mutators: per type
    /**********************************************************************
     */

    public MutableCoercionConfig findOrCreateCoercion(CoercionTargetType type) {
        if (_perTypeCoercions == null) {
            _perTypeCoercions = new MutableCoercionConfig[TARGET_TYPE_COUNT];
        }
        MutableCoercionConfig config = _perTypeCoercions[type.ordinal()];
        if (config == null) {
            _perTypeCoercions[type.ordinal()] = config = new MutableCoercionConfig();
        }
        return config;
    }

    public MutableCoercionConfig findOrCreateCoercion(Class<?> type) {
        if (_perClassCoercions == null) {
            _perClassCoercions = new HashMap<>();
        }
        MutableCoercionConfig config = _perClassCoercions.get(type);
        if (config == null) {
            config = new MutableCoercionConfig();
            _perClassCoercions.put(type, config);
        }
        return config;
    }

    /*
    /**********************************************************************
    /* Access
    /**********************************************************************
     */

    /**
     * General-purpose accessor for finding what to do when specified coercion
     * from shape that is now always allowed to be coerced from is requested.
     *
     * @param config Currently active deserialization configuration
     * @param targetType Logical target type of coercion
     * @param targetClass Physical target type of coercion
     * @param inputShape Input shape to coerce from
     *
     * @return CoercionAction configured for specified coercion
     *
     * @since 2.12
     */
    public CoercionAction findCoercion(DeserializationConfig config,
            CoercionTargetType targetType,
            Class<?> targetClass, CoercionInputShape inputShape)
    {
        // First, see if there is exact match for physical type
        if (_perClassCoercions != null) {
            MutableCoercionConfig cc = _perClassCoercions.get(targetClass);
            if (cc != null) {
                CoercionAction act = cc.findAction(inputShape);
                if (act != null) {
                    return act;
                }
            }
        }

        // If not, maybe by logical type
        if (_perTypeCoercions != null) {
            MutableCoercionConfig cc = _perTypeCoercions[targetType.ordinal()];
            if (cc != null) {
                CoercionAction act = cc.findAction(inputShape);
                if (act != null) {
                    return act;
                }
            }
        }

        // Otherwise there are some legacy features that can provide answer
        if (inputShape == CoercionInputShape.EmptyArray) {
            // Default for setting is false
            return config.isEnabled(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT) ?
                    CoercionAction.AsNull : CoercionAction.Fail;
        }
        if (inputShape == CoercionInputShape.EmptyString) {
            // Default for setting is false
            return config.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT) ?
                    CoercionAction.AsNull : CoercionAction.Fail;
        }
        if ((inputShape == CoercionInputShape.Float)
                && (targetType == CoercionTargetType.Integer)) {
            // Default for setting is true
            return config.isEnabled(DeserializationFeature.ACCEPT_FLOAT_AS_INT) ?
                    CoercionAction.TryConvert : CoercionAction.Fail;
        }

        if ((targetType == CoercionTargetType.Float)
                || (targetType == CoercionTargetType.Integer)
                || (targetType == CoercionTargetType.Boolean)) {
            if (!config.isEnabled(MapperFeature.ALLOW_COERCION_OF_SCALARS)) {
                return CoercionAction.Fail;
            }
        }

        // and all else failing, return default
        return _defaultAction;
    }

    /**
     * More specialized accessor called in case of input being a blank
     * String (one consisting of only white space characters with length of at least one).
     * Will basically first determine if "blank as empty" is allowed: if not,
     * returns {@code actionIfBlankNotAllowed}, otherwise returns action for
     * {@link CoercionInputShape#EmptyString}.
     *
     * @param config Currently active deserialization configuration
     * @param targetType Logical target type of coercion
     * @param targetClass Physical target type of coercion
     * @param actionIfBlankNotAllowed Return value to use in case "blanks as empty"
     *    is not allowed
     *
     * @return CoercionAction configured for specified coercion from blank string
     */
    public CoercionAction findCoercionFromBlankString(DeserializationConfig config,
            CoercionTargetType targetType,
            Class<?> targetClass,
            CoercionAction actionIfBlankNotAllowed)
    {
        Boolean acceptBlankAsEmpty = null;
        CoercionAction action = null;

        // First, see if there is exact match for physical type
        if (_perClassCoercions != null) {
            MutableCoercionConfig cc = _perClassCoercions.get(targetClass);
            if (cc != null) {
                acceptBlankAsEmpty = cc.getAcceptBlankAsEmpty();
                action = cc.findAction(CoercionInputShape.EmptyString);
            }
        }

        // If not, maybe by logical type
        if (_perTypeCoercions != null) {
            MutableCoercionConfig cc = _perTypeCoercions[targetType.ordinal()];
            if (cc != null) {
                if (acceptBlankAsEmpty == null) {
                    acceptBlankAsEmpty = cc.getAcceptBlankAsEmpty();
                }
                if (action == null) {
                    action = cc.findAction(CoercionInputShape.EmptyString);
                }
            }
        }

        if (acceptBlankAsEmpty == null) {
            acceptBlankAsEmpty = _acceptBlankAsEmpty;
        }

        // First: if using blank as empty is no-go, return what caller specified
        if (!acceptBlankAsEmpty.booleanValue()) {
            return actionIfBlankNotAllowed;
        }

        // Otherwise, if specific action specified, return that
        if (action != null) {
            return null;
        }

        // If not, one specific legacy setting to consider...
        return config.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT) ?
                    CoercionAction.AsNull : CoercionAction.Fail;
    }
}