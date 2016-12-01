package multicastqueue.total;

import multicastqueue.MulticastMessage;

import java.net.InetSocketAddress;

/**
 * This class of objects is used to tell the rest of the group
 * that a new peer joined at our site. Made as a static class to
 * avoid the serialization considers the outer class.
 */
class JoinRelayMessage extends MulticastMessage
{
    private final InetSocketAddress addressOfJoiner;

    public JoinRelayMessage(InetSocketAddress sender,
                            InetSocketAddress joiner)
    {
        super(sender);
        addressOfJoiner = joiner;
    }

    /**
     * @return The address of the peer who originally sent the
     *         join request message that resulted in this join
     *         message.
     */
    public InetSocketAddress getAddressOfJoiner()
    {
        return addressOfJoiner;
    }

    public String toString()
    {
        return "(JoinRelayMessage for " + getAddressOfJoiner() +
                " from " + getSender() + ")";
    }
}
