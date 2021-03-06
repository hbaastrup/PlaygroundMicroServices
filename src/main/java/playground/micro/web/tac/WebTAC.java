package playground.micro.web.tac;

import java.io.File;
import java.util.List;

import hba.gc.GcEvent;
import hba.gc.GcEventListener;
import hba.gc.GcEventLogger;
import io.javalin.BadRequestResponse;
import io.javalin.Javalin;
import playground.micro.models.MonitorMetric;
import playground.micro.models.TAC;
import playground.micro.models.TACArray;

public class WebTAC implements GcEventListener {
	Javalin app;
	String name;
	TACCache cache = new TACCache();
	
	GcEventLogger gcEventLogger;
	
	public WebTAC(int port, String name, TACCache cache) {
		gcEventLogger = new GcEventLogger();
		gcEventLogger.start(this);
		
		this.name = name;
		this.cache = cache;
		app = Javalin.create();
		app.enableRouteOverview("/path"); // render a HTML page showing all mapped routes
		app.enableStaticFiles(".");
		app.start(port);
		
		app.get("/micro/tac/get/:tac", ctx -> {
			TAC tac = cache.get(ctx.pathParam("tac"));
			if (tac==null)
				throw new BadRequestResponse("ERROR: TAC does not exist");
			ctx.json(tac);
		});
		
		app.get("/micro/tac/all", ctx -> {
			List<Integer> all = cache.getAllTacs();
			ctx.json(new TACArray(all));
		});
		
		app.get("/micro/get/name", ctx -> {
			ctx.result(name);
		});
		
		app.get("/micro/metic", ctx -> {
			MonitorMetric metric = new MonitorMetric().setName(name);
			ctx.json(metric);
		});
		
		app.get("/micro/gc", ctx -> {
			ctx.json(gcEventLogger.getLogs());
		});
		
		app.get("/micro/fail", ctx -> ctx.status(401).json("'err':'Unauthorized'"));
		app.get("/micro/exception", ctx -> {throw new BadRequestResponse("ERROR: Provoked by GET");});
	}
	
	public void close() {
		gcEventLogger.stop();
	}
	
	
	@Override
	public void onComplete(GcEvent event) {}
	
	
	public static void main(String[] args) throws Exception {
		String name = WebTAC.class.getName();
		String dataFile = "data/netscout-avvasi_20190315_84_of_84.txt";
		int port = 10081;
		
		for (int i=0; i<args.length; i++) {
			if ("-p".equals(args[i])) {
				i++;
				if (i<args.length)
					port = Integer.parseInt(args[i]);
			}
			else if ("-n".equals(args[i])) {
				i++;
				if (i<args.length)
					name = args[i];
			}
			else if ("-d".equals(args[i])) {
				i++;
				if (i<args.length)
					dataFile = args[i];
			}
		}
		
		TACCache cache = new TACCache();
		cache.load(new File(dataFile));
		WebTAC web = new WebTAC(port, name, cache);
		
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("STOPPING!!!");
				web.close();
				System.out.println("STOPPED!!!");
			}
		});
	}
}
