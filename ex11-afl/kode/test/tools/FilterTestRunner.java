package tools;

import junit.runner.Version;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a stacktrace-filtered junit4 TestRunner
 * which mainly just copies the main() method from
 * org.junit.runner.JUnitCore and runs with a
 * hotciv.tools.FilteredTextListener instead of a TextListener
 */
public class FilterTestRunner
{
    public static void main(String... args)
    {
        JUnitCore juc = new JUnitCore();

        System.out.print("JUnit" + Version.id() + " - Target: ");

        List<Class<?>> classes        = new ArrayList<Class<?>>();
        List<Failure>  missingClasses = new ArrayList<Failure>();

        for (String each : args)
        {
            try
            {
                classes.add(Class.forName(each));
                System.out.println( "'" + each + "', ");
            }
            catch (ClassNotFoundException e)
            {
                System.out.println("Could not find class: " + each);

                Description description =
                    Description.createSuiteDescription(each);
                Failure failure = new Failure(description, e);

                missingClasses.add(failure);
            }
        }

        juc.addListener(new FilteredTextListener());

        Result result = juc.run(classes.toArray(new Class[classes.size()]));

        for (Failure each : missingClasses)
        {
            result.getFailures().add(each);
        }

        System.exit(result.wasSuccessful()
                ? 0
                : 1);
    }
}
