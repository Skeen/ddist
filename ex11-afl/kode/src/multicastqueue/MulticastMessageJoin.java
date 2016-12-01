package multicastqueue;

import java.net.InetSocketAddress;

/**
 * Returned by multicastqueue to signal that a new peer has joined the
 * peer group.
 *
 * @author Jesper Buus Nielsen, Aarhus University, 2012.
 */
public class MulticastMessageJoin extends MulticastMessage
{
    public MulticastMessageJoin(InetSocketAddress sender) 
    {
        super(sender);
    }

    public String toString() 
    {
        return "(JoinMessage from " + getSender()  + ")";	
    }

    @Override()
    public boolean equals(Object other)
    {
        return this == other
                || other instanceof MulticastMessageJoin
                && super.equals(other);
    }
}
