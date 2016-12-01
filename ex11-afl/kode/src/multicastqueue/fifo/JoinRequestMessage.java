package multicastqueue.fifo;

import multicastqueue.MulticastMessage;

import java.net.InetSocketAddress;

/**
 * This class of objects is used to send a join request.  It will
 * be sent by a new peer to one of the existing peers in the peer
 * group, called the "joining point" below.  Made as a static
 * class to avoid the serialization considers the outer class.
 */
class JoinRequestMessage extends MulticastMessage
{
    public JoinRequestMessage(InetSocketAddress myAddress)
    {
        super(myAddress);
    }

    public String toString()
    {
        return "(JoinRequestMessage from " + getSender()  + ")";
    }
}
