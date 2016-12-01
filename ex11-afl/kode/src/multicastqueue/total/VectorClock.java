package multicastqueue.total;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Holds a list of timestamps representing current knowledge of
 * every process on the network.
 *
 * Each process has an unique ID (which is a key to that process'
 * timestamp in the lists).
 * The VectorClock identifies each peers VectorClock from its
 * InetSocketAddress.
 */
public final class VectorClock implements Clock
{
    private final Map<Integer,Timestamp> timestamps;
    private final Integer ownerID;

    /**
     *
     * @param identifier The unique ID of the process owning this VC.
     *        This could be the hashCode of the InetSocketAddress.
     */
    public VectorClock(int identifier)
    {
        this.ownerID = identifier;
        timestamps = new HashMap<Integer, Timestamp>();
        timestamps.put(ownerID,new Timestamp());
    }

    @Override
    public Timestamp getLocalTimestamp()
    {
        return getTimestamp(ownerID);
    }

    /**
     * @param ownerIdentifier The key for looking up the timestamp.
     * @return The current timestamp
     */
    public Timestamp getTimestamp(Integer ownerIdentifier)
    {
        Timestamp res;
        if (timestamps.containsKey(ownerIdentifier))
            res = timestamps.get(ownerIdentifier);
        else {
            res = new Timestamp();
            timestamps.put(ownerIdentifier,res);
        }

        return res;
    }

    /**
     * Increases this VectorClocks timestamp
     *
     * This symbolises an event happening for the process
     * attached to this VC.
     */
    @Override
    public void incrementLocalTimestamp()
    {
        incrementTimestampOf(ownerID);
    }

    void incrementTimestampOf(Integer ownerIdentifier)
    {
        synchronized (timestamps)
        {
            Timestamp localTimestamp = timestamps.get(ownerIdentifier);

            if (localTimestamp == null)
                throw new IllegalStateException("No nullvalue should be stored as a timestamp.");

            localTimestamp.increment();
        }
    }


    /**
     * @return a view of all the identifiers that has a timestamp here.
     */
    Set<Integer> getOwnerIdentifiers() {
        return timestamps.keySet();
    }

    /**
     * For updating the timestamp according to a message received
     * from the network.
     *
     * Gathers new knowledge from another VectorClock in the
     * attempt to updateAccordingTo this VectorClocks information.
     *
     * The timestamp is set according to the following formula:
     * VC[k] = max{ VC[k], ts(m)[k] }
     * this[k] = max{ this[k], that[k] }
     *
     *
     * @param that The VectorClock to get possibly new information from
     */
    public void updateAccordingTo(VectorClock that)
    {
        if (this==that || this.ownerID.equals(that.ownerID))
        {
            // If updateAccordingTo happens regarding one self, it must be already
            // known data. Then it does not make sense to updateAccordingTo.
            return;
        }

        synchronized (timestamps)
        {
            // Update my knowledge of each processes current timestamp.
            for (Integer each : that.getOwnerIdentifiers())
            {
                // Register new timestamp if not already registered.
                if (!this.timestamps.containsKey(each))
                {
                    timestamps.put(each, new Timestamp());
                }

                // Update the timestamp
                this.getTimestamp(each).maxedWith(that.getTimestamp(each));
            }
        }
    }

    @Override
    public String toString() {
        String res = "";
        int sum = 0;

        for (Timestamp each : timestamps.values())
        {
            sum += each.getValue();
            res += " ";
            res += each == getLocalTimestamp()
                   ? "`" + each
                   : each;
        }

        return "time'"+sum+"{" + res + "}";
    }

    int compareTo(VectorClock that)
    {
        int
                thisTotalCount = 0,
                thatTotalCount = 0;

        for (Timestamp each : this.timestamps.values())
        {
            thisTotalCount += each.getValue();
        }
        for (Timestamp each : that.timestamps.values())
        {
            thatTotalCount += each.getValue();
        }

        return thisTotalCount - thatTotalCount;
    }

    @Override
    public boolean equals(Object other)
    {
        return (this == other || other instanceof VectorClock)
                && this.compareTo((VectorClock) other) == 0;
    }

    @Override
    public void updateAccordingTo(Clock that) {
        if (that instanceof VectorClock)
            updateAccordingTo((VectorClock) that);
        else throw new UnsupportedOperationException("Can only update regarding other vectorClocks.");
    }

    @Override
    public int compareTo(Object other) {
        if (other instanceof Clock)
            return compareTo((Clock) other);
        throw new UnsupportedOperationException("Can only compare regarding other Clocks");
    }

    @Override
    public int compareTo(Clock other) {
        if (other instanceof VectorClock )
            return compareTo((VectorClock) other);
        throw new UnsupportedOperationException("Can only compare regarding other VectorClocks");
    }

    @Override
    public final VectorClock clone()
    {
        VectorClock clone = new VectorClock(ownerID);
        for (Integer each : getOwnerIdentifiers())
        {
            clone.timestamps.put(each,
                    this.timestamps.get(each).clone());
        }
        return clone;
    }

    public VectorClock cloneThroughSerialization()
    {
        VectorClock res = null;
        try
        {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(this);
            objectOutputStream.close();

            ObjectInputStream objectInputStream = new ObjectInputStream(
                    new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
            res = (VectorClock) objectInputStream.readObject();
            objectInputStream.close();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }
}
