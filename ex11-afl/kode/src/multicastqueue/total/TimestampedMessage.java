package multicastqueue.total;

import multicastqueue.MulticastMessage;

import java.net.InetSocketAddress;

public class TimestampedMessage extends MulticastMessage
        implements Comparable<TimestampedMessage>
{
    private final MulticastMessage msg;
    private final Clock clock;

    TimestampedMessage(InetSocketAddress sender, MulticastMessage msg, Clock clock)
    {
        super(sender);

        if (msg instanceof TimestampedMessage)
        {
            throw new IllegalArgumentException(
                    "To avoid chain-containment this message " +
                            "do not support holding TimeStampedMessages");
        }

        this.msg = msg;
        this.clock = clock;
    }

    public TimestampedMessage(MulticastMessage msg, Clock clock)
    {
        this(msg.getSender(),msg, clock);
    }

    public MulticastMessage getMessage()
    {
        return msg;
    }

    public Clock getClock()
    {
        return clock;
    }

    /*
     * Sorts after timestamp
     *
     * Thereafter sender
     * Thereafter attached message
     */
    @Override
    public final int compareTo(TimestampedMessage that)
    {
        int res = this.clock.compareTo(that.clock);
        if (res == 0) {
            // Two events occurred at exactly the same time
            res = this.getSender().toString().compareTo(that.getSender().toString());
            /*
            We actually allows same-timestamped messages in our sorted Queue.
            However we sort secondly after the processID, which is congruent with the sender.
            This is the same as
                'attaching the number of the process in which the event occurs to the low-order
                end of the time, separated by a decimal point.'
            as proposed in [Distributed Systems - Tanenbaum et. al.]
             */
        }
        if (res == 0) {
            throw new IllegalStateException("Comparison of timestamps of events " +
                    "occurring at exactly the same time at the same process");
            //res = this.getMessage().toString().compareTo(that.getMessage().toString());
        }
        return res;
    }

    @Override
    public String toString()
    {
        String s = getSender().toString();

        return "(TimestampedMessage from " +
                s.substring(s.lastIndexOf('.'))
                + " about |"+msg + "| @" + clock +")";
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;

        if (o instanceof TimestampedMessage)
        {
            TimestampedMessage other = (TimestampedMessage) o;
            if (super.equals(other)
                    && other.getMessage().equals(this.getMessage()))
                return true;
        }
        return false;
    }
}
