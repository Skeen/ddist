package multicastqueue.causal;

import java.util.*;

@SuppressWarnings({"ALL"})
public class MultiSet<E> extends AbstractCollection<E>
{
	private HashMap<E,Integer> m; // key, value 
    private int count;
	
	// store the element as key (to be iterated through keySet() )
	// and the value as the number of items
	public MultiSet()
	{
		m = new HashMap<E,Integer>(); // key, value 
		count = 0;
	}
	
	/**
	 * @param c - The specified Collection.
	 */
	public MultiSet(Collection<E> c)
	{
		this();
		addAll(c);
	}

	/** 
	 * @return true - This is a modifiable collection
     * (This is a requirement of the collections framework.)
	 * @param - anObject the object to be added
	 */
	public boolean add(E anObject)
	{
		if (m.containsKey(anObject))
		{
			m.put(anObject, m.get(anObject) + 1); // value += 1 (now there is one more of the key-type)
		}
		else
		{
			m.put(anObject, 1); // Now there is 1 of the key type
		}
		count++;
		return true;
	}
	
	/**
     * Remove object anObject
	 * @param anObject - The object to be removed
     * @return true - if modification happened
	 */
	@SuppressWarnings("unchecked") //the else statement 
	public boolean remove(Object anObject)
	{	
		if (m.containsKey(anObject))
		{
			int value = m.get(anObject);
			if (value > 1) // More than 1 to remove, just decrement value
			{ 
				// This is an unsafe operation...
				// cast to generic type 
				// causes (Object)Object cast during runtime...?
				m.put((E) anObject, value - 1);
			}
			else // Only one to remove
			{
				m.remove(anObject); 
			}
			count--;
			return true;
		}
		else return false;
	}
	
	/**
	 * count is incremented in add() and decremented in remove(),
	 * so it stays current.
	 * @return count - Current no. of elements added with add(...)
	 * subtracted the no. of elements removed with remove(...).
	 */
	public int size()
	{	assert countAssert();
		return count;
	}

	//assert count == sum of all values in m
	private boolean countAssert()
	{
		int sum = 0;
		for (Integer i: m.values())
			sum += i.intValue();
		
		return count == sum;
	}
	
	/**
	 * @return An iterator over the elements added to this set.
	 * Do not use iterator.remove(); because remove does not
	 * act the same depending on the map value (if value>1, then
	 * decrement value).
	 */
	public Iterator<E> iterator()
	{
		return new 
			Iterator<E>()
			{
				private Iterator iter = m.keySet().iterator();
				private int value = 0;
				private E current;
				
				public boolean hasNext()
				{				
					return (value > 0 || iter.hasNext()); 
				}
				
				@SuppressWarnings("unchecked") // cast to generic E
				public E next()
				{
					if (value == 0) // updateAccordingTo current
					{
						current = (E) iter.next();
						value = m.get(current);
					}
					value--; // make ready for next call
					return current;
				}
				
				// could be implemented with this.remove(current)
				public void remove()
				{
				   throw new UnsupportedOperationException();
				}
			};
	}
	
	/**
	 * I didn't like the equals sign in hashMap.toString(), so
	 * I made my own toString() without those.
	 * @return String representation of the MultiSet
	 */
	@SuppressWarnings("unchecked") //the E cast
	public String toString()
	{
		if (m.isEmpty()) return "{}";
		String result = "{";
		Iterator iter = m.keySet().iterator();

		do
		{
			E e1 = (E) iter.next();
			result += m.get(e1)+" "+ e1 +", ";
		}
		while (iter.hasNext());

		//replace last ', ' with a '}':
		return result.substring(0, result.length()-2)+"}";
	}
	
	
	// firste checks for type, then just the hashmap.
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null) return false;
		if (getClass() != o.getClass()) return false;
		
		MultiSet hest = (MultiSet) o;
		return m.equals(hest.m);
	}
	
	public int hashCode()
	{
		return 7*m.hashCode();
	}

    /**
     * Returns the number of occurrences of an element in this multiset (the count of the element).
     *
     * Inspiration: com.google.common.collect.Multiset
     */
    public int count(E object)
    {
        return m.get(object);
    }

    /**
     * Adds or removes the necessary occurrences of an element such that
     * the element attains the desired count.
     *
     * Inspiration: com.google.common.collect.Multiset
     *
     * @param element
     * @param count
     */
    public void setCount(E element, int count)
    {
        m.put(element, count);
    }

    /**
     * Inspiration: com.google.common.collect.Multiset
     *
     * @return the set of distinct elements contained in this multiset.
     */
    public Set<E> elementSet()
    {
        return m.keySet();
    }
}