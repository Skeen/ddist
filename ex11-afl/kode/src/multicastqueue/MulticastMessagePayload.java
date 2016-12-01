package multicastqueue;

import java.io.Serializable;
import java.net.InetSocketAddress;

/**
 * This class of objects returned by multicastqueue to signal
 * that an object was multicast. It has a generic type E, which
 * specified that class of the object that was multicast.
 *
 * @author Jesper Buus Nielsen, Aarhus University, 2012.
 */
public class MulticastMessagePayload<E extends Serializable> extends MulticastMessage 
{
    private final E payload;

    /**
     * @param e the object that is/was multicast.
     */
    public MulticastMessagePayload(InetSocketAddress sender, E e) 
    {
        super(sender);
        payload = e;
    }

    /**
     * @return The payload of this message.
     */
    public E getPayload() 
    {
        return payload;
    }

    public String toString() 
    {
        return "(PayloadMessage from " + getSender() + 
            " with payload '" + payload + "')";
    }

    @Override()
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof MulticastMessagePayload)) return false;

        return super.equals(other)
                && (((MulticastMessagePayload) other).payload).equals(this.payload);
    }

    @Override
    public int hashCode() {
        int res = super.hashCode();
        res = res * 31 + payload.hashCode();
        return res;
    }
}
