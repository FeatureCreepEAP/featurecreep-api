package featurecreep.api.platform;

public class Software {

	public static Software MINECRAFT = new Software("Minecraft", "1.20.1", "Minecraft 1.16", GameEngine.LWJGL);
	public static Software DANGERZONE = new Software("DangerZone", "2.7", "DangerZone 2.7", GameEngine.LWJGL);
	public static Software HEARTS_OF_IRON_IV = new Software("Hearts of Iron IV", "1.12.13", "Hearts of Iron IV 1.12",
			GameEngine.CLAUSEWITZ);

	public String name;
	public String version;
	public String description;
	public GameEngine engine;

	public Software(String name, String version, String description, GameEngine engine) {
		this.name = name;
		this.version = version;
		this.description = description;
		this.engine = engine;
	}

}
