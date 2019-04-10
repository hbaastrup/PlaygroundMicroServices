package playground.micro.cdr;

import java.util.List;

import hba.tuples.Pair;
import io.javalin.BadRequestResponse;
import io.javalin.Javalin;

import playground.micro.kpi.KpiHolder;
import playground.micro.kpi.TimeSeries;
import playground.micro.kpi.TimeTaken;
import playground.micro.models.CDR;

public class WebCdr {
	static final String WEB_TIME = "CDR_WEB_TIME";
	static final int WEB_TIME_DURATION = 60000;
	Javalin app;

	public WebCdr(int port) {
		KpiHolder.INSTANCE.add(WEB_TIME, new TimeSeries(WEB_TIME_DURATION));
		
		app = Javalin.create();
		app.enableRouteOverview("/path"); // render a HTML page showing all mapped routes
		app.enableStaticFiles(".");
		app.start(port);
		
		app.get("/micro/cdr/get/:begin/:end", ctx -> {
			long startTime = System.currentTimeMillis();
			List<CDR> cdrs = CdrDatabase.INSTANCE.get(ctx.pathParam("begin"), ctx.pathParam("end"));
			ctx.res.setHeader("Access-Control-Allow-Origin", "*");
			KpiHolder.INSTANCE.add(WEB_TIME, new TimeTaken((int)(System.currentTimeMillis()-startTime)));
			ctx.json(cdrs);
		});
		
		app.get("/micro/cdr/get/time", ctx -> {
			long startTime = System.currentTimeMillis();
			Pair<Integer, Integer> minMax = CdrDatabase.INSTANCE.getMinMaxTime();
			ctx.res.setHeader("Access-Control-Allow-Origin", "*");
			KpiHolder.INSTANCE.add(WEB_TIME, new TimeTaken((int)(System.currentTimeMillis()-startTime)));
			ctx.json("{minTime:"+minMax.getValue0()+", maxTime:"+minMax.getValue1()+"}");
		});
		
		// status=OK: curl -X PUT -F "calling=123456" -F "called=33455" -F "endtime=1554885540413" -F "duration=10000" localhost:10082/micro/cdr/put
		// status=ERROR: curl -X PUT -F "calling=123456" -F "called=33455" -F "endtime=1554885540413" localhost:10082/micro/cdr/put
		app.put("/micro/cdr/put", ctx -> {
			String calling = ctx.formParam("calling");
			if (calling==null) calling = ctx.queryParam("calling");
			String called = ctx.formParam("called");
			if (called==null) called = ctx.queryParam("called");
			String endtime = ctx.formParam("endtime");
			if (endtime==null) endtime = ctx.queryParam("endtime");
			String duration = ctx.formParam("duration");
			if (duration==null) duration = ctx.queryParam("duration");
			CDR cdr = createCDR(calling, called, endtime, duration);
			CdrDatabase.INSTANCE.add(cdr);
		});
		
		app.get("/micro/fail", ctx -> ctx.status(401).json("'err':'Unauthorized'"));
		app.get("/micro/exception", ctx -> {throw new BadRequestResponse("ERROR: Provoked by GET");});
	}
	
	private CDR createCDR(String callingStr, String calledStr, String endtimeStr, String dureationStr) {
		long calling = Long.parseLong(callingStr);
		long called = Long.parseLong(calledStr);
		long endTime = Long.parseLong(endtimeStr);
		int duration = 0;
		if (dureationStr!=null)
			duration = Integer.parseInt(dureationStr);
		CDR cdr;
		if (duration==0) cdr = new CDR(calling, called, endTime);
		else cdr = new CDR(calling, called, endTime-duration, endTime);
		return cdr;
	}
	
	public void close() {}
	
	
	public static void main(String[] args) {
		int port = 10082;
		
		for (int i=0; i<args.length; i++) {
			if ("-p".equals(args[i])) {
				i++;
				if (i<args.length)
					port = Integer.parseInt(args[i]);
			}
		}

		WebCdr web = new WebCdr(port);
		
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("STOPPING!!!");
				web.close();
				System.out.println("STOPPED!!!");
			}
		});
	}
}
