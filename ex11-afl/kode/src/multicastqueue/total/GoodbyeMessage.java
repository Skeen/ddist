package multicastqueue.total;

import multicastqueue.MulticastMessage;

import java.net.InetSocketAddress;

/**
 * This class of objects is used to send the address of the
 * existing peers to a leaving member. Happens in response to
 * a leave message. Made as a static class to avoid the
 * serialization considers the outer class.
 */
class GoodbyeMessage extends MulticastMessage
{
    public GoodbyeMessage(InetSocketAddress myAddress)
    {
        super(myAddress);
    }

    public String toString()
    {
        return "(GoodbyeMessage from " + getSender()  + ")";
    }
}
