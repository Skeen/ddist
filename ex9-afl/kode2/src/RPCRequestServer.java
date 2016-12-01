import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

class RPCRequestServer
{
    TransferHelper transferHelper = null;
    private static Map<String, Map<String, Object>> magicMap = null;

    public RPCRequestServer()
    {
        init();
    }

    /**
     * Decodes the request string and serves it, based on the protocol (as specified in the report)
     * precondition: request is not null
     *
     * @param request The Request-URI from the http-request.
     * @throws java.io.IOException If an I/O error occurs while sending a response to the client.
     */
    public void serveRequest(String request, TransferHelper transferHelper) throws IOException {
        assert request != null;

        // Ensure that post requests are used for state changing things.
        // And that get is used for getters

        this.transferHelper = transferHelper;

        int argumentsStart = request.indexOf('?');

        StringTokenizer tokenizer;
        StringTokenizer tokenizer2;

        // If the '?' is not in the request, then there are no args.
        tokenizer = (argumentsStart < 0)
                ? new StringTokenizer(request, "/")
                : new StringTokenizer(request.substring(0, argumentsStart), "/");

        String klass, object, method;
        try {
            klass = tokenizer.nextToken();
            object = tokenizer.nextToken();
            method = tokenizer.nextToken();
        } catch (NoSuchElementException e) {
            // Error, Request string is not valid request.
            transferHelper.sendResponse(400, "<b>The HTTP request appeared malformed, please try again later</b>");
            return;
        }

        System.out.println("Debug: <class>[<object>].<method>(): \r\n   "
                + klass + "[" + object + "]." + method + "()");

        // If the '?' is not in the request, then there are no args.
        // or if the ? is the last symbol, then there is no args either.
        tokenizer2 = (argumentsStart < 0 || request.length() == argumentsStart)
                ? null
                : new StringTokenizer(request.substring(argumentsStart + 1), "&");

        List<Pair<String, String>> arguments = new ArrayList<Pair<String, String>>();
        if (tokenizer2 != null) {
            while (tokenizer2.hasMoreTokens()) {
                // Get the string, and then parse it into a key value pair.
                String s = tokenizer2.nextToken();
                // Find the equal sign
                int equalLocation = s.indexOf('=');

                String key;
                String value;
                if (equalLocation < 0) // if no occurrences of '='
                {
                    key = "";
                    value = s;
                } else {
                    key = s.substring(0, equalLocation);
                    value = s.substring(equalLocation + 1);
                }
                Pair<String, String> key_value_pair = new Pair<String, String>(key, value);

                System.out.println("Debug: argument (name;parameter):\r\n   "
                        + key_value_pair);

                arguments.add(key_value_pair);
            }
        }

        // Get the class corresponding with the name
        Class c = getClassByName(klass);

        // Get the method corresponding with the name
        Method m = getMethodByNameAndClass(method, c);

         // Try to find the requested object
        Object o = lookupObjectByKeyAndClass(object, klass);

        // If method and class not found, do not continue. (error-response already sent)
        if (c!=null && m!=null && o!=null)
            return;

        // Get a list of the argument values
        List<String> passingArguments = new ArrayList<String>();
        for (Pair<String, String> each : arguments) {
            passingArguments.add(each.getSecond());
        }

        // And let's invoke it, we should likely check that the found
        // arguments match using m.getParameterTypes()
        Object returnValue;
        try {
            returnValue = m.invoke(o, passingArguments.toArray());
            // Throws:
            // NullPointerException - if the specified object is null and the method is an instance method.
            // ExceptionInInitializerError - if the initialization provoked by this method fails
        } catch (NumberFormatException e) {
            // a form of InvocationTargetException or Illegal Argument exceptions.
            // Thrown to indicate that the application has attempted to convert a string to one of
            // the numeric types, but that the string does not have the appropriate format.
            transferHelper.sendResponse(400, "The Request can not be fulfilled. Please use proper number formatting.");
            return;
        } catch (IllegalAccessException e) {
            // IllegalAccessException - if this Method object is enforcing Java language access control and
            // the underlying method is inaccessible.
            transferHelper.sendResponse(403, "Illegal Access");
            return;
        } catch (IllegalArgumentException e) {
            // IllegalArgumentException - if the method is an instance method and the specified object
            // argument is not an instance of the class or interface declaring the underlying method (or
            // of a subclass or implementor thereof); if the number of actual and formal parameters differ;
            // if an unwrapping conversion for primitive arguments fails; or if, after possible unwrapping,
            // a parameter value cannot be converted to the corresponding formal parameter type by a method
            // invocation conversion.
            transferHelper.sendResponse(404, "The Request can not be fulfilled. Please use proper arguments.");
            return;
        } catch (InvocationTargetException e) {
            // InvocationTargetException - if the underlying method throws an exception.
            transferHelper.sendResponse(500, "Internal Server Error");
            e.printStackTrace();
            return;
        }

        transferHelper.sendResponse(200, "" + returnValue);
    }

    private Object lookupObjectByKeyAndClass(String key, String className)
            throws IOException
    {
        Object res = null;

        // Get the class and find the active object corresponding to it
        Map<String, Object> lo = magicMap.get(className);
        if (lo == null) {
            String regClasses = "Registered classes are; ";
            for (Map.Entry<String, Map<String, Object>> entry : magicMap.entrySet()) {
                regClasses = regClasses + entry.getKey() + " ";
            }

            transferHelper.sendResponse(204, "The requested class was not registered, in the table. " + regClasses);
        }

        // Lookup the object in the class table
        res = lo.get(key);
        if (res == null) {
            String regObject = "Registered objects are; ";
            for (Map.Entry<String, Object> entry : lo.entrySet()) {
                regObject = regObject + entry.getKey() + " ";
            }

            transferHelper.sendResponse(200, "The requested object was not found, in the table. " + regObject);
        }
        return res;
    }

    private Method getMethodByNameAndClass(String method, Class c) throws IOException {
        // Lookup the method, using Class.getMethod
        Method res = null;
        try {
            // Now lookup the method we're to going to call
            for (Method mi : c.getDeclaredMethods()) {
                if (mi.getName().equals(method)) {
                    res = mi;
                }
            }

            if (res == null) {
                String methods = "The defined functions are:";
                for (Method mi : c.getDeclaredMethods()) {
                    methods = methods + mi.getName() + "(), ";
                }

                //sendResponse(200, "The requested function wasn't found!" + methods);
                transferHelper.sendResponse(404, "The requested function wasn't found!" + methods);
            }
        } catch (SecurityException e) {
            // sendResponse(200, "You have been denied access to the declared "
            //         + "methods within this class by the security manager.");
            // sendResponse(403, "You have been denied access by the security manager.");
            transferHelper.sendResponse(403, "You have been denied access to the declared "
                    + "methods within this class by the security manager.");
        }
        return res;


    }

    private Class getClassByName(String name) throws IOException {
        Class res = null;
        try {
            // Find the class represent of what we want!
            res = Class.forName(name);
            // Throws:
            // LinkageError - if the linkage fails
            // ExceptionInInitializerError - if the initialization provoked by this method fails
            // ClassNotFoundException - if the class cannot be located
        } catch (ClassNotFoundException e) {
            //sendResponse(200, "The requested class wasn't found in the virtual machine!");
            transferHelper.sendResponse(404, "The requested class wasn't found in the virtual machine!");
        }
        return res;
    }

    // Asserts that the environment is sane
    private static void init()
    {
        if(magicMap != null)
        {
            return;
        }

        magicMap = new HashMap<String, Map<String, Object>>();
        Map<String, Object> bankMap = new HashMap<String, Object>();
        Map accountMap = new HashMap<String, Object>();

        magicMap.put("Bank", bankMap);
        magicMap.put("Account", accountMap);

        bankMap.put("1", new BankImpl(accountMap));
    }

}