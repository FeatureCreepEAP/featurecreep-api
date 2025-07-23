package featurecreep.api.anti_encapsulation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;

public class GoogleCommonsImmutableMutaliser {

	 /**
     * Adds an object to a GoogleCommons RegularImmutableMap
     * @param key The key for the new entry
     * @param to_add The value for the new entry
     * @param list The list representing the ImmutableMap
     */
    public static void addToRegularImmutableMap(Object key, Object to_add, Map map) {
        try {
            // Access the 'entries' field of the list
            Field entriesField = map.getClass().getDeclaredField("entries");
            entriesField.setAccessible(true);
            Object[] entries = (Object[]) entriesField.get(map);

            // Access the 'table' field of the list
            Field tableField = map.getClass().getDeclaredField("table");
            tableField.setAccessible(true);
            Object[] table = (Object[]) tableField.get(map);

            // Calculate the new length (one more than the current)
            int len = entries.length;
            Object[] nuevo = new Object[len + 1];
            Object[] nuevoTable = new Object[len + 1];

            // Use reflection to access the ImmutableMapEntry constructor
            Class<?> immutableMapEntryClass = Class.forName("com.google.common.collect.ImmutableMapEntry");
            Constructor<?> cons = immutableMapEntryClass.getDeclaredConstructor(Object.class, Object.class);
            cons.setAccessible(true);

            // Create a new entry using the constructor
            Object newEntry = cons.newInstance(key, to_add);

            // Copy existing entries and table into the new arrays
            System.arraycopy(entries, 0, nuevo, 0, len);
            System.arraycopy(table, 0, nuevoTable, 0, len);

            // Add the new entry to both entries and table arrays
            nuevo[len] = newEntry;
            nuevoTable[len] = newEntry;

            // Set the updated arrays back to the 'entries' and 'table' fields
            entriesField.set(map, nuevo);
            tableField.set(map, nuevoTable);

        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException | NoSuchMethodException | ClassNotFoundException | InstantiationException | InvocationTargetException e) {
            // Handle any exceptions that occur during reflection
            e.printStackTrace();
        }
    }
	
	/**
	 * Adds an object to a GoogleCommons RegularImmutableSet
	 * @param to_add
	 * @param set
	 */
    public static void addToRegularImmutableSet(Object to_add, Set set) {
      if(!set.contains(to_add)) {
    	try {
            // Access the 'entries' field of the list
            Field entriesField = set.getClass().getDeclaredField("elements");
            entriesField.setAccessible(true);
            Object[] entries = (Object[]) entriesField.get(set);

            // Access the 'table' field of the list
            Field tableField = set.getClass().getDeclaredField("table");
            tableField.setAccessible(true);
            Object[] table = (Object[]) tableField.get(set);

            // Calculate the new length (one more than the current)
            int len = entries.length;
            Object[] nuevo = new Object[len + 1];
            Object[] nuevoTable = new Object[len + 1];

            // Copy existing entries and table into the new arrays
            System.arraycopy(entries, 0, nuevo, 0, len);
            System.arraycopy(table, 0, nuevoTable, 0, len);

            // Add the new entry to both entries and table arrays
            nuevo[len] = to_add;
            nuevoTable[len] = to_add;

            // Set the updated arrays back to the 'entries' and 'table' fields
            entriesField.set(set, nuevo);
            tableField.set(set, nuevoTable);

        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            // Handle any exceptions that occur during reflection
            e.printStackTrace();
        }
    	
      }
    	
    }
	
	
	
}
