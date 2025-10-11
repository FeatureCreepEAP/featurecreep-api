package featurecreep.api.crashdetector;

import java.util.ArrayList;
import java.util.List;

public interface Error {
	
	public static List<Error> errors = new ArrayList<Error>();
	
	public void parse(String log);
	
	public String name();
	
	public boolean triggered();
	
	public double priority();
	
	public String message();
	
	
}
