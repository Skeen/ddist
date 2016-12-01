package multicastqueue.total;

import multicastqueue.MulticastMessage;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class AcknowledgeMessage extends TimestampedMessage implements Serializable
{
    /**
     * @param sender The sender of the acknowledgment.
     * @param message The message that is acknowledged.
     * @param clock The timestamps from the sender.
     */
    public AcknowledgeMessage(InetSocketAddress sender, MulticastMessage message, Clock clock) {
        super(sender, message, clock);
    }

    /**
     * Sets the sender of the acknowledgeMessage to be the sender of msg.
     * @param msg The message to attach.
     * @param clock The timestamp to attach.
     */
    public AcknowledgeMessage(MulticastMessage msg, Clock clock)
    {
        super(msg,clock);
    }

    public String toString()
    {
        String s = getSender().toString();

        return "(AcknowledgeMessage from " +
                s.substring(s.lastIndexOf('.'))
                + " about |"+getMessage() + "| @" + getClock()+")";
    }

    public boolean equals(Object o)
    {
        return  (o instanceof AcknowledgeMessage
                && super.equals(o) );
    }
}
