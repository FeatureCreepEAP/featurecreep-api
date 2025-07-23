package featurecreep.api.platform;

public class GameEngine {

	public static GameEngine LWJGL = new GameEngine("LWJGL",true);
	public static GameEngine CLAUSEWITZ = new GameEngine("Clausewitz",false);
	
	public String name;
	public boolean is_jvm;
	
	public GameEngine(String name, boolean is_jvm) {
	this.name=name;
	this.is_jvm=is_jvm;
	}
	
	
}
