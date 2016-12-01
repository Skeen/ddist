package multicastqueue;

import java.net.InetSocketAddress;
import java.io.Serializable;

/**
 * The root of the hierarchy of messages which are returned by multicastqueue.
 *
 * @author Jesper Buus Nielsen, Aarhus University, 2012.
 */
public class MulticastMessage implements Serializable 
{
    private final InetSocketAddress sender;

    /**
     * @param sender The sender of the message.
     */
    protected MulticastMessage(InetSocketAddress sender)
    {
        this.sender = sender;
    }

    /**
     * @return The sender of the message.
     */
    public InetSocketAddress getSender() 
    {
        return sender;
    }

    @Override()
    public String toString()
    {
        return "(MulticastMessage from " + sender + ")";
    }

    @Override()
    public boolean equals(Object other)
    {
        return (this == other || other instanceof MulticastMessage)
                && other.toString().equals(this.toString());
    }

    @Override
    public int hashCode() {
        int res = getClass().hashCode();
        res = res * 31 + sender.hashCode();
        return res;
    }
}
