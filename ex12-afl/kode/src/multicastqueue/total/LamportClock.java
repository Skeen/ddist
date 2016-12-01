package multicastqueue.total;

/**
 *
 */
public final class LamportClock implements Clock
{
    private final Timestamp timestamp;

    public LamportClock()
    {
        this.timestamp = new Timestamp();
    }

    @Override
    public Timestamp getLocalTimestamp() {
        return timestamp;
    }

    @Override
    public void incrementLocalTimestamp()
    {
        synchronized (timestamp)
        {
            timestamp.increment();
        }
    }

    @Override
    public void updateAccordingTo(Clock that) {
        if (that instanceof LamportClock)
        {
            synchronized (timestamp)
            {
                this.timestamp.maxedWith(that.getLocalTimestamp());
            }
        }
        else
        {
            throw new UnsupportedOperationException(
                    "updateAccordingTo has not been implemented with other LamportClocks!");
        }
    }

    int compareTo(LamportClock other)
    {
        return getLocalTimestamp().compareTo(other.getLocalTimestamp());
    }

    @Override
    public int compareTo(Clock other)
    {
        if (other instanceof LamportClock)
        {
            return compareTo((LamportClock) other);
        }
        else
        {
            throw new UnsupportedOperationException("Can only compare to LamportCLocks!");
        }
    }

    @Override
    public int compareTo(Object other)
    {
        if (other instanceof Clock)
        {
            return compareTo((Clock) other);
        }
        else
        {
            throw new UnsupportedOperationException("Can only compare to CLocks!");
        }
    }

    @Override
    public String toString() {
        return "time'"+timestamp;
    }

    @Override
    public final LamportClock clone()
    {
        LamportClock clone = new LamportClock();
        clone.updateAccordingTo(this);
        return clone;
    }

    @Override
    public boolean equals(Object other)
    {
        if (other == this) return true;
        if (!(other instanceof LamportClock))
           return false;

        return this.getLocalTimestamp()
                .equals(((LamportClock) other).getLocalTimestamp());
    }
}
