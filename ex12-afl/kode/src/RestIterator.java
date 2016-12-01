import java.util.Iterator;

class RestIterator<E> implements Iterable<E>
{
    private Iterator<E> iterator = null;

    public RestIterator(Iterable<E> iter)
    {
        iterator = iter.iterator();
        // Don't consider the first element
        if(iterator.hasNext())
        {
            // Simply by skipping it
            iterator.next();
        }
    }

    public Iterator<E> iterator()
    {
        return iterator;
    }
}

