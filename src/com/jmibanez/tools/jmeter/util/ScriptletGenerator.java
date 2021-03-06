/*
 *
 *
 * See README in the source tree for more info
 *
 */
 
package com.jmibanez.tools.jmeter.util;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.beans.Introspector;
import java.beans.IntrospectionException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static com.jmibanez.tools.jmeter.util.ReflectionUtil.getFieldsUpTo;

/**
 * Describe class ScriptletGenerator here.
 *
 *
 * Created: Tue Jan 13 18:08:30 2009
 *
 * @author <a href="mailto:jm@jmibanez.com">JM Ibanez</a>
 * @version 1.0
 */
public class ScriptletGenerator {

    private static Log log = LogFactory.getLog(ScriptletGenerator.class);

    private Map<Object, String> generatedObjects = new HashMap<>();

    public String generateScriptletForObject(Object bean, String varname) {
        return generateScriptletForObject(bean, varname, null);
    }

    public <T> String generateScriptletForObject(T bean, String varname,
                                                 Class<? extends T> varTypeHint) {
        String[] scriptletAndDecl = scriptletForObject(varname, bean, varTypeHint);
        return scriptletAndDecl[0] + "\n// -------------------------------\n\n" + scriptletAndDecl[1];
    }

    public String getVariableNameForType(final Object bean) {
        if(bean == null) {
            return "args";
        }

        Class<?> beanClass = bean.getClass();
        String className = beanClass.getSimpleName();

        if(beanClass.isArray()) {
            Class<?> elementClass = beanClass.getComponentType();
            className = elementClass.getSimpleName() + "Array";
        }

        return Introspector.decapitalize(className);
    }

    private <T> String[] scriptletFromFields(T bean, String varname) {
        Class<?> beanClass = bean.getClass();
        StringBuilder decl = new StringBuilder();
        StringBuilder scriptlet = new StringBuilder();

        int objCount = 1;
        for(Field f: getFieldsUpTo(beanClass, Object.class)) {
            if (Modifier.isFinal(f.getModifiers())
                || Modifier.isStatic(f.getModifiers())
                || Modifier.isTransient(f.getModifiers())) {
                // Ignore transient, static, or final fields
                continue;
            }

            Class<?> valType = f.getType();

            if (Object.class.isAssignableFrom(valType)
                && !Collection.class.isAssignableFrom(valType)
                && !Serializable.class.isAssignableFrom(valType)) {
                // Skip non-Serializable values
                continue;
            }

            f.setAccessible(true);

            Object val = null;
            try {
                val = f.get(bean);
            }
            catch(IllegalAccessException accessEx) {
                // Filler value
                val = "/* Couldn't populate value */";
            }

            String varname_subvar = varname + "_" + f.getName();
            objCount++;

            if(val == null) {
                // Special-case: null
                scriptlet.append(varname);
                scriptlet.append(".");
                scriptlet.append(f.getName());
                scriptlet.append(" = null;\n");
                continue;
            }

            String[] argScriptlet = scriptletForObject(varname_subvar, val, null);
            decl.append(argScriptlet[0]);

            scriptlet.append(argScriptlet[1]);
            scriptlet.append(varname);
            scriptlet.append(".");
            scriptlet.append(f.getName());
            scriptlet.append(" = ");
            scriptlet.append(varname_subvar);
            scriptlet.append(";\n");
        }

        return new String[] { decl.toString(), scriptlet.toString() };
    }


    private void appendConstructorCallForClass(Class<?> beanType,
                                               StringBuilder scriptlet) {
        try {
            beanType.getConstructor(new Class<?>[] { });
            scriptlet.append("new ");
            scriptlet.append(beanType.getCanonicalName());
            scriptlet.append("();\n");
        } catch (NoSuchMethodException ex) {
            scriptlet.append("com.jmibanez.tools.jmeter.util.ReflectionUtil.newInstance(");
            scriptlet.append(beanType.getCanonicalName());
            scriptlet.append(".class); // WARNING: ");
            scriptlet.append(beanType.getCanonicalName());
            scriptlet.append(" has no default constructor, using Objenesis glue to build instance\n");
        }
    }

    /**
     * Generate a BeanShell scriptlet to recreate a particular bean
     * instance.
     *
     * @return String[] of two elements, String[0] being the
     * primitives, etc. setup scriptlet, and String[1] being the bean
     * setup scriptlet
     */
    private <T> String[] scriptletForObject(String varname, T bean,
                                            Class<?> varTypeHint) {
        if(bean == null) {
            if(varTypeHint != null) {
                return new String[] { "", varTypeHint.getCanonicalName() + " " + varname + " = null;/* Null */\n" };
            }

            return new String[] { "", varname + " = null;/* Null */\n" };
        }

        Class<?> beanType = bean.getClass();

        // Handle types
        // Array: Unpack
        if(beanType.isArray()) {
            return unpackArray(varname, bean, beanType.getComponentType());
        }

        // Collection: Unpack, depending on type
        if(bean instanceof Collection) {
            return unpackCollection(varname, (Collection) bean);
        }

        // Primitives: as-is
        if(beanType == boolean.class
           || beanType == char.class
           || beanType == byte.class
           || beanType == short.class
           || beanType == int.class
           || beanType == long.class
           || beanType == float.class
           || beanType == double.class
           || beanType == Character.class
           || beanType == Byte.class
           || beanType == Short.class
           || beanType == Boolean.class
           || beanType == Integer.class
           || beanType == Long.class
           || beanType == Float.class
           || beanType == Double.class
           || beanType == String.class) {
            return new String[] { primitiveAsScriptlet(varname, bean), "" };
        }

        if(beanType == String.class) {
            return new String[] { "String " + varname + " = " + stringAsScriptlet((String) bean) + ";\n", "" };
        }

        if(beanType == Class.class) {
            return new String[] { "Class " + varname + " = " + classRefScriptlet((Class) bean) + ";\n", "" };
        }

        String typeSignature = beanType.getCanonicalName();

        if(generatedObjects.containsKey(bean)) {
            String prevVar = generatedObjects.get(bean);
            return new String[] { "", typeSignature + " " + varname + " = " + prevVar + "; "};
        }

        generatedObjects.put(bean, varname);

        // Object: introspect
        // Assume bean follows standard JavaBean conventions,
        // fallback on using public fields

        StringBuilder decl = new StringBuilder();
        StringBuilder scriptlet = new StringBuilder();
        scriptlet.append(typeSignature);
        scriptlet.append(" ");
        scriptlet.append(varname);
        scriptlet.append(" = ");
        appendConstructorCallForClass(beanType, scriptlet);

        String[] scr = scriptletFromFields(bean, varname);
        decl.append("\n");
        decl.append("// ----------  Field values for ");
        decl.append(beanType.getSimpleName());
        decl.append(" ");
        decl.append(varname);
        decl.append("\n");
        decl.append(scr[0]);

        scriptlet.append("\n");
        scriptlet.append(scr[1]);

        return new String[] { decl.toString(), scriptlet.toString() };
    }


    private String typeDeclarationForPrimitive(Class<?> primitiveClass) {
        return primitiveClass.getSimpleName();
    }

    private String scriptletValueForPrimitive(Object pInstance) {
        if(pInstance == null) {
            return null;
        }

        Class<?> primitiveClass = pInstance.getClass();

        if(primitiveClass == char.class
           || primitiveClass == Character.class) {
            return characterAsScriptlet(pInstance);
        }

        if(primitiveClass == long.class
           || primitiveClass == Long.class) {
            return pInstance.toString().contains("L") ? pInstance.toString() : pInstance.toString() + "L";
        }
        if(primitiveClass == float.class
           || primitiveClass == Float.class) {
            return pInstance.toString().contains("f") ? pInstance.toString() : pInstance.toString() + "f";
        }
        if(primitiveClass == double.class
           || primitiveClass == Double.class) {
            return pInstance.toString().contains("d") ? pInstance.toString() : pInstance.toString() + "d";
        }
        if(primitiveClass == String.class) {
            return stringAsScriptlet((String) pInstance);
        }

        return pInstance.toString();
    }

    private String primitiveAsScriptlet(String varname, Object pInstance) {
        assert pInstance != null : "pInstance cannot be null";

        Class<?> primitiveClass = pInstance.getClass();

        String pTypeString = typeDeclarationForPrimitive(primitiveClass);
        String pTypeVal    = scriptletValueForPrimitive(pInstance);

        if(pTypeString != null) {
            return pTypeString + " " + varname + " = " + pTypeVal + ";\n";
        }

        throw new IllegalArgumentException("Not recognized as a primitive class:" + primitiveClass.getCanonicalName());
    }

    private String characterAsScriptlet(Object value) {
        assert value != null : "Value (char) must exist";
        return "'" + escape(value.toString()) + "'";
    }

    private String stringAsScriptlet(String value) {
        return "\"" + escape(value) + "\"";
    }

    private String classRefScriptlet(Class<?> clazz) {
        return clazz.getName() + ".class";
    }

    private String escape(String value) {
        return StringEscapeUtils.escapeJava(value);
    }

    private String[] unpackPrimitiveArray(String varname, Object pArrayBean,
                                          Class<?> elementType) {
        StringBuilder scr = new StringBuilder();
        int arrLen = Array.getLength(pArrayBean);

        String pTypeString = typeDeclarationForPrimitive(elementType);

        scr.append(pTypeString);
        scr.append("[] ");
        scr.append(varname);
        scr.append(" = new ");
        scr.append(pTypeString);
        scr.append("[] { ");

        for(int i = 0; i < arrLen; i++) {
            scr.append(scriptletValueForPrimitive(Array.get(pArrayBean, i)));

            if(i != arrLen - 1) {
                scr.append(", ");
            }
        }

        scr.append(" };");

        return new String[] { "", scr.toString() };
    }

    @SuppressWarnings("rawtypes")
    private String[] unpackArray(String varname, Object arrayBean,
                                 Class<?> elementType) {
        StringBuilder elementScr = new StringBuilder();
        StringBuilder arrayScr = new StringBuilder();
        int arrLen = Array.getLength(arrayBean);

        // Special case: primitives
        if(elementType == boolean.class
           || elementType == char.class
           || elementType == byte.class
           || elementType == short.class
           || elementType == int.class
           || elementType == long.class
           || elementType == float.class
           || elementType == double.class
           || elementType == Character.class
           || elementType == Byte.class
           || elementType == Short.class
           || elementType == Boolean.class
           || elementType == Integer.class
           || elementType == Long.class
           || elementType == Float.class
           || elementType == Double.class
           || elementType == String.class) {
            return unpackPrimitiveArray(varname, arrayBean, elementType);
        }

        String[] elementVarnames = new String[arrLen];
        for(int i = 0; i < arrLen; i++) {
            Object o = Array.get(arrayBean, i);
            elementVarnames[i] = varname + "_element" + i + "_"
                + getVariableNameForType(o);
            String[] eScriptlet = scriptletForObject(elementVarnames[i], o,
                                                     elementType);
            if(!eScriptlet[0].equals("")) {
                elementScr.append(eScriptlet[0]);
            }

            if(!eScriptlet[1].equals("")) {
                arrayScr.append(eScriptlet[1]);
            }
        }

        arrayScr.append(elementType.getCanonicalName());
        arrayScr.append("[] ");
        arrayScr.append(varname);
        arrayScr.append(" = new ");
        arrayScr.append(elementType.getCanonicalName());
        arrayScr.append("[] { ");
        for(int i = 0; i < arrLen; i++) {
            arrayScr.append(elementVarnames[i]);
            if(i != arrLen - 1) {
                arrayScr.append(", ");
            }
        }
        arrayScr.append(" };");

        return new String[] { elementScr.toString(), arrayScr.toString() };
    }

    @SuppressWarnings("rawtypes")
    private String[] unpackCollection(String varname, Collection c) {
        StringBuilder elementScr = new StringBuilder();
        StringBuilder cScr = new StringBuilder();

        if(c instanceof Properties) {
            // Special-case: Properties
            return unpackProperties(varname, (Properties) c);
        }

        if(c instanceof Map) {
            // Special-case: Maps
            return unpackMapAsKeyValue(varname, (Map) c);
        }

        int i = 0;
        List<String> elementVarnames = new ArrayList<>();
        for(Iterator ii = c.iterator(); ii.hasNext(); ) {
            Object o = ii.next();
            String elementVarname = varname + "_element" + i + "_"
                + getVariableNameForType(o);
            elementVarnames.add(elementVarname);
            String[] eScriptlet = scriptletForObject(elementVarname, o,
                                                     Object.class);
            if(!eScriptlet[0].equals("")) {
                elementScr.append(eScriptlet[0]);
            }

            if(!eScriptlet[1].equals("")) {
                cScr.append(eScriptlet[1]);
            }

            i++;
        }

        cScr.append(c.getClass().getCanonicalName());
        cScr.append(" ");
        cScr.append(varname);
        cScr.append(" = new ");
        cScr.append(c.getClass().getCanonicalName());
        cScr.append("();\n");


        for(Iterator<String> ii = elementVarnames.iterator(); ii.hasNext(); ) {
            String elementVarname = ii.next();
            cScr.append(varname);
            cScr.append(".add(");
            cScr.append(elementVarname);
            cScr.append(");\n");
        }

        return new String[] { elementScr.toString(), cScr.toString() };
    }

    @SuppressWarnings("rawtypes")
    private String[] unpackMapAsKeyValue(String varname, Map m) {
        StringBuilder elementScr = new StringBuilder();
        StringBuilder mapScr = new StringBuilder();

        mapScr.append(m.getClass().getCanonicalName());
        mapScr.append(" ");
        mapScr.append(varname);
        mapScr.append(" = new ");
        mapScr.append(m.getClass().getCanonicalName());
        mapScr.append("();\n");


        int i = 0;
        for(Object key : m.keySet()) {
            Object val = m.get(key);

            if(!(key instanceof String)) {
                String[] keyScriptlet = scriptletForObject(varname + "_key" + i, key, key.getClass());
                if(!keyScriptlet[0].equals("")) {
                    elementScr.append(keyScriptlet[0]);
                }

                if(!keyScriptlet[1].equals("")) {
                    mapScr.append(keyScriptlet[1]);
                }
            }
            if(!(val instanceof String)) {
                String[] valScriptlet = scriptletForObject(varname + "_val" + i, val, val.getClass());
                if(!valScriptlet[0].equals("")) {
                    elementScr.append(valScriptlet[0]);
                }

                if(!valScriptlet[1].equals("")) {
                    mapScr.append(valScriptlet[1]);
                }
            }

            mapScr.append(varname);
            mapScr.append(".put(");
            if(key instanceof String) {
                mapScr.append(stringAsScriptlet((String) key));
            }
            else {
                mapScr.append(varname);
                mapScr.append("_key");
                mapScr.append(i);
            }

            mapScr.append(", ");

            if(val instanceof String) {
                mapScr.append(stringAsScriptlet((String) val));
            }
            else {
                mapScr.append(varname);
                mapScr.append("_val");
                mapScr.append(i);
            }
            mapScr.append(");\n");

            i++;
        }

        return new String[] { elementScr.toString(), mapScr.toString() };
    }

    private String[] unpackProperties(String varname, Properties p) {
        StringBuilder pScr = new StringBuilder();

        pScr.append("java.util.Properties ");
        pScr.append(varname);
        pScr.append(" = new Properties();\n");

        for(String name : p.stringPropertyNames()) {
            pScr.append(varname);
            pScr.append(".setProperty(");
            pScr.append(stringAsScriptlet(name));
            pScr.append(", ");
            pScr.append(stringAsScriptlet(p.getProperty(name)));
            pScr.append(");\n");
        }

        return new String[] { "", pScr.toString() };
    }
}
