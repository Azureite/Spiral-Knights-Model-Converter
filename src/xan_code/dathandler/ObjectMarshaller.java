package xan_code.dathandler;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
/**
 * Used to read and write object fields.
 */
public class ObjectMarshaller
{
    /**
     * Retrieves or creates a marshaller for objects of the specified class.
     */
    public static ObjectMarshaller getObjectMarshaller (Class<?> clazz)
    {
        ObjectMarshaller marshaller = _marshallers.get(clazz);
        if (marshaller == null) {
            _marshallers.put(clazz, marshaller = new ObjectMarshaller(clazz));
        }
        return marshaller;
    }

    /**
     * Returns a reference to the prototype object (used to determine field defaults).
     */
    public Object getPrototype ()
    {
        return _prototype;
    }

    /**
     * Reads the fields of an object from the specified importer.
     */
    public static void readFields (Object object, Importer importer, boolean useReader)
        throws IOException
    {
        if (_reader != null && useReader) {
            try {
                _reader.invoke(object, importer);
            } catch (Exception e) {
                throw (IOException)new IOException(
                    "Error invoking custom read method.").initCause(e);
            }
        } else {
            try {
                for (FieldData field : _fields) {
                    field.read(object, importer);
                }
            } catch (IllegalAccessException iae) {
                throw (IOException)new IOException("Error reading field.").initCause(iae);
            }
        }
    }

    /**
     * Writes the fields of an object to the specified exporter.
     */
    public void writeFields (Object object, Exporter exporter, boolean useWriter)
        throws IOException
    {
        if (_writer != null && useWriter) {
            try {
                _writer.invoke(object, exporter);
            } catch (Exception e) { // InvocationTargetException, IllegalAccessException
                throw (IOException)new IOException(
                    "Error invoking custom write method.").initCause(e);
            }
        } else {
            try {
                for (FieldData field : _fields) {
                    field.write(object, exporter);
                }
            } catch (IllegalAccessException iae) {
                throw (IOException)new IOException("Error writing field.").initCause(iae);
            }
        }
    }

    /**
     * Creates a marshaller for objects of the specified class.
     */
    @SuppressWarnings("static-access")
	protected ObjectMarshaller (Class<?> clazz)
    {
        // look for custom read/write methods
        try {
            _reader = clazz.getMethod("readFields", Importer.class);
            _reader.setAccessible(true);
            if (Modifier.isStatic(_reader.getModifiers())) {
                _reader = null;
            }
        } catch (NoSuchMethodException e) { }
        try {
            _writer = clazz.getMethod("writeFields", Exporter.class);
            _writer.setAccessible(true);
            if (Modifier.isStatic(_writer.getModifiers())) {
                _writer = null;
            }
        } catch (NoSuchMethodException e) { }

        // collect the exportable fields
        ArrayList<Field> fields = new ArrayList<Field>();
        getExportableFields(clazz, fields);
        _fields = new FieldData[fields.size()];
        for (int ii = 0; ii < _fields.length; ii++) {
            _fields[ii] = new FieldData(fields.get(ii));
        }

        // create the prototype
        try {
            Class<?> oclazz = ReflectionUtil.getOuterClass(clazz);
            if (oclazz == null) {
                // static classes can use the no-arg constructor
                _prototype = clazz.newInstance();
            } else {
                // inner classes must pass the prototype of the outer class
                Object oproto = getObjectMarshaller(oclazz)._prototype;
                _prototype = ReflectionUtil.newInstance(clazz, oproto);
            }
        } catch (Exception e) {
            throw (IllegalArgumentException)new IllegalArgumentException(
                "Failed to create object prototype [class=" + clazz + "].").initCause(e);
        }
    }

    /**
     * Places all of the given class's exportable fields into the supplied list.
     */
    protected static void getExportableFields (Class<?> clazz, ArrayList<Field> fields)
    {
        // prepend the superclass fields, if any
        Class<?> sclazz = clazz.getSuperclass();
        if (Exportable.class.isAssignableFrom(sclazz)) {
            getExportableFields(sclazz, fields);
        }

        // add any non-static, non-transient, non-synthetic fields
        for (Field field : clazz.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (!(Modifier.isStatic(mods) || Modifier.isTransient(mods) || field.isSynthetic())) {
                field.setAccessible(true);
                fields.add(field);
            }
        }
    }

    /**
     * Contains information on a single field.
     */
    protected class FieldData
    {
        public FieldData (Field field)
        {
            _field = field;
            String fname = field.getName();
            _name = ((fname.charAt(0) == '_') ? fname.substring(1) : fname).intern();
            _marshaller = FieldMarshaller.getFieldMarshaller(field);
        }

        /**
         * Reads the field from the importer and sets it in the target object.
         */
        public void read (Object target, Importer importer)
            throws IOException, IllegalAccessException
        {
            _marshaller.readField(_field, _name, target, _prototype, importer);
        }

        /**
         * Retrieves the field from the target object and writes it to the exporter.
         */
        public void write (Object source, Exporter exporter)
            throws IOException, IllegalAccessException
        {
            _marshaller.writeField(_field, _name, source, _prototype, exporter);
        }

        /** The field to read/write. */
        protected Field _field;

        /** The field's modified name. */
        protected String _name;

        /** The field marshaller. */
        protected FieldMarshaller _marshaller;
    }

    /** The custom read method. */
    protected static Method _reader;

    /** The custom write method. */
    protected static Method _writer;

    /** The object's field data. */
    protected static FieldData[] _fields;

    /** The prototype object. */
    protected static Object _prototype;

    /** Maps classes to created marshallers. */
    protected static HashMap<Class<?>, ObjectMarshaller> _marshallers =
        new HashMap<Class<?>, ObjectMarshaller>();
}