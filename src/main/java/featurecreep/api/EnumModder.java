package featurecreep.api;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import sun.reflect.ConstructorAccessor;

public class EnumModder {

  //https://stackoverflow.com/questions/9614282/how-to-create-an-instance-of-enum-using-reflection-in-java
  public static Object newEnumInstance(Class clazz, String name, Object...params) { //kinda doubt we should make return type an Enum just incase

    if (clazz.isEnum()) {

      try {
        Class monsterClass = clazz;
        // first we need to find our constructor, and make it accessible
        Constructor < ? > constructor = monsterClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);

        // this is this same code as in constructor.newInstance, but we just skipped all that useless enum checks ;)
        Field constructorAccessorField = Constructor.class.getDeclaredField("constructorAccessor");
        constructorAccessorField.setAccessible(true);
        // sun.reflect.ConstructorAccessor -> internal class, we should not use it, if you need use it, it would be better to actually not import it, but use it only via reflections. (as package may change, and will in java 9+)
        ConstructorAccessor ca = (ConstructorAccessor) constructorAccessorField.get(constructor);
        if (ca == null) {
          Method acquireConstructorAccessorMethod = Constructor.class.getDeclaredMethod("acquireConstructorAccessor");
          acquireConstructorAccessorMethod.setAccessible(true);
          ca = (ConstructorAccessor) acquireConstructorAccessorMethod.invoke(constructor);
        }
        // note that real constructor contains 2 additional parameters, name and ordinal
        //	Colours enumValue = (Colours) ca.newInstance(new Object[]{"CAERBANNOG_RABBIT", arr.length+1, "caerbannograbbit"});// you can call that using reflections too, reflecting reflections are best part of java ;)
       
     
        
        Object[] oldarr = new Object[] {
          name,
          20
        };//Gotta fix the number later
        Object[] arr = new Object[oldarr.length + params.length];
        System.arraycopy(oldarr, 0, arr, 0, oldarr.length);
        System.arraycopy(params, 0, arr, oldarr.length, params.length);
        return (Object) ca.newInstance(arr);

      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      System.out.println(clazz.getCanonicalName() + " is not an enum");
    }

    return null;
  }

  static void makeAccessible(Field field) throws Exception {
    field.setAccessible(true);
    Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
  }

}