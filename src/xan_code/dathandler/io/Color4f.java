package xan_code.dathandler.io;

import java.awt.Color;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.nio.FloatBuffer;

import com.samskivert.util.StringUtil;

import xan_code.dathandler.io.Streamable;

import xan_code.dathandler.Encodable;

/**
 * A four-element floating point color value.
 */
public final class Color4f
    implements Encodable, Streamable
{
    /** An opaque white color object. */
    public static final Color4f WHITE = new Color4f(1f, 1f, 1f, 1f);

    /** An opaque gray color object (OpenGL's default diffuse material color). */
    public static final Color4f GRAY = new Color4f(0.8f, 0.8f, 0.8f, 1f);

    /** An opaque dark gray color object (OpenGL's default ambient material color). */
    public static final Color4f DARK_GRAY = new Color4f(0.2f, 0.2f, 0.2f, 1f);

    /** An opaque black color object. */
    public static final Color4f BLACK = new Color4f(0f, 0f, 0f, 1f);

    /** A transparent black color object. */
    public static final Color4f TRANSPARENT_BLACK = new Color4f(0f, 0f, 0f, 0f);

    /** An opaque red color object. */
    public static final Color4f RED = new Color4f(1f, 0f, 0f, 1f);

    /** An opaque green color object. */
    public static final Color4f GREEN = new Color4f(0f, 1f, 0f, 1f);

    /** An opaque blue color object. */
    public static final Color4f BLUE = new Color4f(0f, 0f, 1f, 1f);

    /** An opaque cyan color object. */
    public static final Color4f CYAN = new Color4f(0f, 1f, 1f, 1f);

    /** An opaque magenta color object. */
    public static final Color4f MAGENTA = new Color4f(1f, 0f, 1f, 1f);

    /** An opaque yellor color object. */
    public static final Color4f YELLOW = new Color4f(1f, 1f, 0f, 1f);

    /** The components of the color. */
    public float r, g, b, a;

    /**
     * Creates a color with the supplied components.
     */
    public Color4f (float r, float g, float b, float a)
    {
        set(r, g, b, a);
    }

    /**
     * Creates a color from an array of values.
     */
    public Color4f (float[] values)
    {
        set(values);
    }

    /**
     * Copy constructor.
     */
    public Color4f (Color4f other)
    {
        set(other);
    }

    /**
     * Creates a color object from an AWT color.
     */
    public Color4f (Color color)
    {
        set(color);
    }

    /**
     * Creates an opaque white color object.
     */
    public Color4f ()
    {
        set(1f, 1f, 1f, 1f);
    }

    /**
     * Returns the AWT color object corresponding to this color.
     */
    public Color getColor ()
    {
        return getColor(true);
    }

    /**
     * Returns the AWT color object corresponding to this color.
     *
     * @param includeAlpha if true, include the alpha component (otherwise use an opaque
     * alpha value).
     */
    public Color getColor (boolean includeAlpha)
    {
        return new Color(r, g, b, includeAlpha ? a : 1f);
    }

    /**
     * Multiplies this color in-place by the specified value.
     *
     * @return a reference to this color, for chaining.
     */
    public Color4f multLocal (float value)
    {
        return mult(value, this);
    }

    /**
     * Multiplies this color by the specified value.
     *
     * @return a new color containing the result.
     */
    public Color4f mult (float value)
    {
        return mult(value, new Color4f());
    }

    /**
     * Multiplies this color by the specified value, placing the result in the object provided.
     *
     * @return a reference to the result color, for chaining.
     */
    public Color4f mult (float value, Color4f result)
    {
        return result.set(r * value, g * value, b * value, a * value);
    }

    /**
     * Multiplies this color in-place by another.
     *
     * @return a reference to this color, for chaining.
     */
    public Color4f multLocal (Color4f other)
    {
        return mult(other, this);
    }

    /**
     * Multiplies this color by another.
     *
     * @return a new color containing the result.
     */
    public Color4f mult (Color4f other)
    {
        return mult(other, new Color4f());
    }

    /**
     * Multiplies this color by another, storing the result in the object provided.
     *
     * @return a reference to the result object, for chaining.
     */
    public Color4f mult (Color4f other, Color4f result)
    {
        return result.set(r * other.r, g * other.g, b * other.b, a * other.a);
    }

    /**
     * Adds a color in-place to this one.
     *
     * @return a reference to this color, for chaining.
     */
    public Color4f addLocal (Color4f other)
    {
        return add(other, this);
    }

    /**
     * Adds a color to this one.
     *
     * @return a new color containing the result.
     */
    public Color4f add (Color4f other)
    {
        return add(other, new Color4f());
    }

    /**
     * Adds a color to this one, storing the result in the object provided.
     *
     * @return a reference to the result, for chaining.
     */
    public Color4f add (Color4f other, Color4f result)
    {
        return result.set(r + other.r, g + other.g, b + other.b, a + other.a);
    }

    /**
     * Adds another color to this one in-place, clamping the elements of the result to one.
     *
     * @return a reference to this color, for chaining.
     */
    public Color4f clampedAddLocal (Color4f other)
    {
        return clampedAdd(other, this);
    }

    /**
     * Adds another color to this one, clamping the elements of the result to one.
     *
     * @return a new color containing the result.
     */
    public Color4f clampedAdd (Color4f other)
    {
        return clampedAdd(other, new Color4f());
    }

    /**
     * Adds another color to this one, clamping the elements of the result to one, and storing
     * the result in the object provided.
     *
     * @return a reference to the result, for chaining.
     */
    public Color4f clampedAdd (Color4f other, Color4f result)
    {
        return result.set(
            Math.min(r + other.r, 1f), Math.min(g + other.g, 1f),
            Math.min(b + other.b, 1f), Math.min(a + other.a, 1f));
    }

    /**
     * Linearly interpolates between this and the specified other color in-place by the supplied
     * amount.
     *
     * @return a reference to this color, for chaining.
     */
    public Color4f lerpLocal (Color4f other, float t)
    {
        return lerp(other, t, this);
    }

    /**
     * Linearly interpolates between this and the specified other color by the supplied
     * amount.
     *
     * @return a new color containing the result.
     */
    public Color4f lerp (Color4f other, float t)
    {
        return lerp(other, t, new Color4f());
    }

    /**
     * Linearly interpolates between this and the supplied other color by the supplied amount,
     * storing the result in the supplied object.
     *
     * @return a reference to the result, for chaining.
     */
    public Color4f lerp (Color4f other, float t, Color4f result)
    {
        return result.set(
            r + t*(other.r - r),
            g + t*(other.g - g),
            b + t*(other.b - b),
            a + t*(other.a - a));
    }

    /**
     * Copies the components of another color.
     *
     * @return a reference to this color, for chaining.
     */
    public Color4f set (Color4f other)
    {
        return set(other.r, other.g, other.b, other.a);
    }

    /**
     * Copies the components of an AWT color.
     *
     * @return a reference to this color, for chaining.
     */
    public Color4f set (Color color)
    {
        return set(color.getRGBComponents(null));
    }

    /**
     * Copies the elements of an array.
     *
     * @return a reference to this color, for chaining.
     */
    public Color4f set (float[] values)
    {
        return set(values[0], values[1], values[2], values[3]);
    }

    /**
     * Sets all the components of this color.
     *
     * @return a reference to this color, for chaining.
     */
    public Color4f set (float r, float g, float b, float a)
    {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
        return this;
    }

    /**
     * Places the components of this color object into the specified buffer.
     *
     * @return a reference to the buffer, for chaining.
     */
    public FloatBuffer get (FloatBuffer buf)
    {
        return buf.put(r).put(g).put(b).put(a);
    }

    // documentation inherited from interface Encodable
    public String encodeToString ()
    {
        return r + ", " + g + ", " + b + ", " + a;
    }

    // documentation inherited from interface Encodable
    public void decodeFromString (String string)
        throws Exception
    {
        set(StringUtil.parseFloatArray(string));
    }

    // documentation inherited from interface Encodable
    public void encodeToStream (DataOutputStream out)
        throws IOException
    {
        out.writeFloat(r);
        out.writeFloat(g);
        out.writeFloat(b);
        out.writeFloat(a);
    }

    // documentation inherited from interface Encodable
    public void decodeFromStream (DataInputStream in)
        throws IOException
    {
        set(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
    }

    @Override
    public String toString ()
    {
        return "[" + r + ", " + g + ", " + b + ", " + a + "]";
    }

    @Override
    public int hashCode ()
    {
        return Float.floatToIntBits(r) ^ Float.floatToIntBits(g) ^
            Float.floatToIntBits(b) ^ Float.floatToIntBits(a);
    }

    @Override
    public boolean equals (Object other)
    {
        if (!(other instanceof Color4f)) {
            return false;
        }
        Color4f ocolor = (Color4f)other;
        return (r == ocolor.r && g == ocolor.g && b == ocolor.b && a == ocolor.a);
    }
}