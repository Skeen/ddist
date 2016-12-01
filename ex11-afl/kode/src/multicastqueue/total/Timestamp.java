package multicastqueue.total;

import java.io.Serializable;

/**
 * This class takes care of holding knowledge of a single peer's current VectorClock.
 */
public class Timestamp implements Comparable<Timestamp>,Serializable
{
    private int value;

    public Timestamp()
    {
        value = 0;
    }

    public int getValue()
    {
        assert !(value<0):"The VectorClock value should never be negative.";
        return value;
    }

    public void increment()
    {
        ++value;
    }

    public void increment(int offset)
    {
        assert offset>0:"Can only increment with positive values.";
        value += offset;
    }

    public void maxedWith(Timestamp other)
    {
        if (other.value > this.value)
            this.value = other.value;
    }

    @Override
    public int compareTo(Timestamp that) {
        return this.value - that.value;
    }

    @Override
    public String toString()
    {
        return String.valueOf(value);
    }

    @Override
    public final Timestamp clone()
    {
        Timestamp clone;
        try
        {
            clone = (Timestamp) super.clone();
        }
        catch (CloneNotSupportedException e)
        {
            clone = new Timestamp();
            clone.maxedWith(this);
        }
        return clone;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof Timestamp)) return false;

        return this.value == ((Timestamp) other).value;
    }



}
