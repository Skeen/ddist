import java.util.*;

public class RestIteratorTest
{
    public static void main(String args[]) 
    {
        List<Integer> intList = new ArrayList<Integer>();
        for(int x=0; x<10; x++)
        {
            intList.add(x);
        }

        System.out.println("Standard iterator");
        for(Integer i : intList)
        {
            System.out.println(i);
        }

        System.out.println("Rest iterator");
        for(Integer i : new RestIterator<Integer>(intList))
        {
            System.out.println(i);
        }

    }
}
