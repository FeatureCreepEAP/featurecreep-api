package featurecreep.api.platform.super_;

import featurecreep.api.platform.Software;

public class SuperLoader {

	public static SuperLoader RISUGAMIS_MODLOADER = new SuperLoader("Risugami's ModLoader", LoaderFamily.RISUGAMI, Software.MINECRAFT);
	public static SuperLoader MINECRAFTFORGE = new SuperLoader("MinecraftForge", LoaderFamily.RISUGAMI, Software.MINECRAFT);
	public static SuperLoader NEOFORGE = new SuperLoader("NeoForge", LoaderFamily.RISUGAMI, Software.MINECRAFT);
	public static SuperLoader LITELOADER = new SuperLoader("LiteLoader", LoaderFamily.CLIENT, Software.MINECRAFT);
	public static SuperLoader RIFT = new SuperLoader("Rift", LoaderFamily.OTHER, Software.MINECRAFT);
	public static SuperLoader FABRICMC = new SuperLoader("FabricMC", LoaderFamily.FABRICMC, Software.MINECRAFT);
	public static SuperLoader SILKPOWERED = new SuperLoader("SilkPowered", LoaderFamily.FABRICMC, Software.MINECRAFT);
	public static SuperLoader QUILTMC = new SuperLoader("QuiltMC", LoaderFamily.FABRICMC, Software.MINECRAFT);
	public static SuperLoader LOADERCOMPLEX = new SuperLoader("LoaderComplex", LoaderFamily.OTHER, Software.MINECRAFT);
	public static SuperLoader MEDDLE = new SuperLoader("Meddle", LoaderFamily.OTHER, Software.MINECRAFT);//For snaploader as well
	public static SuperLoader LITLAUNCHMC = new SuperLoader("LitLaunchMC", LoaderFamily.OTHER, Software.MINECRAFT);//Only For LitLaunch Minecraft
	public static SuperLoader ROPEMC = new SuperLoader("RopeMC", LoaderFamily.OTHER, Software.MINECRAFT);
	public static SuperLoader VANILLA_MINECRAFT = new SuperLoader("minecraft", LoaderFamily.JBOSS_MODULES, Software.MINECRAFT);
	public static SuperLoader DANGERZONE_BUILTIN_LOADER = new SuperLoader("dangerzone", LoaderFamily.DANGERZONE_BUILTIN_LOADER, Software.DANGERZONE);
	public static SuperLoader HEARTS_OF_IRON_IV_BUILTIN_LOADER = new SuperLoader("hearts_of_iron_iv", LoaderFamily.CLAUSEWITZ_BUILTIN_LOADER, Software.HEARTS_OF_IRON_IV);
	public static SuperLoader PIDGEON = new SuperLoader("Pidgeon", LoaderFamily.OTHER, Software.MINECRAFT);
	public static SuperLoader M3L = new SuperLoader("M3L", LoaderFamily.OTHER, Software.MINECRAFT);
	public static SuperLoader MCPATCHER = new SuperLoader("MCPather", LoaderFamily.OTHER, Software.MINECRAFT);
	public static SuperLoader FLINTMC = new SuperLoader("FlintMC", LoaderFamily.OTHER, Software.MINECRAFT);
	public static SuperLoader OPENMODLOADER = new SuperLoader("OpenModLoader", LoaderFamily.OTHER, Software.MINECRAFT);
	public static SuperLoader NILLOADER = new SuperLoader("NilLoader", LoaderFamily.AGENT, Software.MINECRAFT);
	public static SuperLoader CYAN = new SuperLoader("Cyan", LoaderFamily.OTHER, Software.MINECRAFT);

	
	public String name;
	public LoaderFamily family;
	public Software software;
	
	
	public SuperLoader(String name, LoaderFamily family, Software software) {
        this.name = name;
        this.family = family;
        this.software = software;
	}
	
	
	
	
	
	
	
	
	
	
	
	
	

	
	public static class LoaderFamily{
		
		public static LoaderFamily FABRICMC = new LoaderFamily("fabric-loader");
		public static LoaderFamily RISUGAMI = new LoaderFamily("Risugami's ModLoader");
		public static LoaderFamily JBOSS_MODULES = new LoaderFamily("JBoss Modules"); //For Vainilla Minecraft,JBoss Forge Furnace, and Most other WildFly or JBoss stuff
		public static LoaderFamily AGENT = new LoaderFamily("Java Agent"); //Java Agent based loaders including SpringLoaded atm
		public static LoaderFamily HARDBASSPOWERED = new LoaderFamily("Hardbass Powered");
		public static LoaderFamily DANGERZONE_BUILTIN_LOADER = new LoaderFamily("DangerZone");
		public static LoaderFamily CLAUSEWITZ_BUILTIN_LOADER = new LoaderFamily("Clausewitz");
		public static LoaderFamily HMOD = new LoaderFamily("hMod"); //hmod, Bukkit, Canary etc
		public static LoaderFamily CLIENT = new LoaderFamily("client");
public static LoaderFamily OTHER = new LoaderFamily("Other");//Most others like Liteloader and rift
		
		public String name;
		
		public LoaderFamily(String name) {
			this.name=name;
		}
		
		
	}
	
}
