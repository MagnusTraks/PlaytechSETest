import java.io.*;

public class Main {

    // Bot identity
    private static final String CATEGORY = "Video Games";
    private static final int    BOT_CAT  = 4; 


    private static final String[] CAT_NAMES = {
        "Music","Sports","Kids","DIY","Video Games","ASMR","Beauty","Cooking","Finance"
    };
    private static final String[] AGE_NAMES = {
        "13-17","18-24","25-34","35-44","45-54","55+"
    };

    // VIEW_BRACKETS[i] = { minViewCount, maxViewCount, baseValue }
    private static final long[][] VIEW_BRACKETS = {
        {          0L,         99L, 11},
        {        100L,        999L, 21},
        {      1_000L,      4_999L,  8},
        {      5_000L,     24_999L, 32},
        {     25_000L,     99_999L, 20},
        {    100_000L,    499_999L, 37},
        {    500_000L,  1_999_999L, 41},
        {  2_000_000L,  7_999_999L, 22},
        {  8_000_000L, 24_999_999L, 37},
        { 25_000_000L, 74_999_999L, 14},
        { 75_000_000L, Long.MAX_VALUE, 21}
    };

    private static final double[][] CAT_MATCH = {
        {1.00, 0.28, 0.20, 0.10, 0.25, 0.40, 0.45, 0.10, 0.19}, // Music
        {0.28, 1.00, 0.25, 0.30, 0.35, 0.19, 0.10, 0.28, 0.20}, // Sports
        {0.20, 0.25, 1.00, 0.30, 0.50, 0.28, 0.28, 0.30, 0.19}, // Kids
        {0.10, 0.30, 0.30, 1.00, 0.28, 0.20, 0.25, 0.50, 0.25}, // DIY
        {0.25, 0.35, 0.50, 0.28, 1.00, 0.30, 0.10, 0.19, 0.10}, // Video Games
        {0.40, 0.19, 0.28, 0.20, 0.30, 1.00, 0.50, 0.25, 0.19}, // ASMR
        {0.45, 0.10, 0.28, 0.25, 0.10, 0.50, 1.00, 0.35, 0.10}, // Beauty
        {0.10, 0.28, 0.30, 0.50, 0.19, 0.25, 0.35, 1.00, 0.28}, // Cooking
        {0.19, 0.20, 0.19, 0.25, 0.10, 0.19, 0.10, 0.28, 1.00}, // Finance
    };

    private static final double[] INT_WEIGHTS = {1.0, 0.44, 0.225};

    private static final double[][] AGE_MALE = {
        {0.45, 0.40, 0.28, 0.10, 0.60, 0.25, 0.05, 0.05, 0.05}, // 13-17
        {0.50, 0.50, 0.05, 0.20, 0.55, 0.20, 0.05, 0.28, 0.30}, // 18-24
        {0.30, 0.45, 0.25, 0.40, 0.35, 0.10, 0.05, 0.25, 0.45}, // 25-34
        {0.20, 0.40, 0.45, 0.50, 0.20, 0.05, 0.05, 0.30, 0.50}, // 35-44
        {0.28, 0.30, 0.25, 0.45, 0.10, 0.05, 0.05, 0.35, 0.40}, // 45-54
        {0.28, 0.20, 0.20, 0.35, 0.05, 0.05, 0.05, 0.40, 0.30}, // 55+
    };

    private static final double[][] AGE_FEMALE = {
        {0.50, 0.28, 0.28, 0.28, 0.30, 0.50, 0.45, 0.28, 0.19}, // 13-17
        {0.50, 0.28, 0.19, 0.20, 0.20, 0.40, 0.55, 0.25, 0.10}, // 18-24
        {0.30, 0.28, 0.40, 0.30, 0.28, 0.25, 0.45, 0.40, 0.30}, // 25-34
        {0.20, 0.10, 0.50, 0.40, 0.10, 0.28, 0.35, 0.50, 0.30}, // 35-44
        {0.28, 0.10, 0.30, 0.40, 0.19, 0.10, 0.25, 0.50, 0.25}, // 45-54
        {0.28, 0.10, 0.25, 0.30, 0.19, 0.10, 0.20, 0.50, 0.20}, // 55+
    };

    // State
    private final long initialBudget;
    private final long spendFloor;
    private long remaining;
    private int  round = 0;

    /** Minimum efficiency (points per ebuck) we demand from a bid */
    private double effTarget = 0.42;  
    
    /** Adapts to the difference between test harness and the real tournament */
    private double globalCorrection = 1.0; 

    // Accumulators reset each 100-round window
    private long windowPoints = 0;
    private long windowSpent  = 0;
    private int  windowWins   = 0;
    private int  windowLosses = 0;
    private int  windowBids   = 0;
    private long windowEstimatedPoints = 0;

    // All-time totals
    private long totalPoints = 0;
    private long totalSpent  = 0;

    // Last bid info
    private int     lastEstValForWin = 0;
    private boolean lastDidBid  = false;

    public static void main(String[] args) throws IOException {
        System.setProperty("line.separator", "\n");
        long budget = (args.length > 0) ? Long.parseLong(args[0]) : 10_000_000L;
        new Main(budget).run();
    }

    Main(long budget) {
        this.initialBudget = budget;
        this.spendFloor    = (long)(budget * 0.30);
        this.remaining     = budget;
    }

    // Main loop
    void run() throws IOException {
        BufferedReader in  = new BufferedReader(new InputStreamReader(System.in,  "ISO-8859-1"));
        PrintStream    out = new PrintStream(System.out, true, "ISO-8859-1");
        PrintStream    log = System.err;

        out.println(CATEGORY);
        log.println("[init] category=" + CATEGORY + "  budget=" + initialBudget + " spendFloor=" + spendFloor);

        String line;
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) continue;
            char first = line.charAt(0);
            if      (first == 'S') onSummary(line, log);
            else if (first == 'W' || first == 'L') onResult(line, log);
            else if (first == 'v') onBid(line, out, log);
        }
    }

    // Protocol handlers

    private void onSummary(String line, PrintStream log) {
        String[] p = line.split(" ");
        long actualPts = Long.parseLong(p[1]);
        long spent     = Long.parseLong(p[2]);

        windowPoints = actualPts;
        windowSpent  = spent;
        totalPoints += actualPts;
        totalSpent  += spent;

        double winEff     = spent > 0 ? (double) actualPts / spent : 0.0;
        double spendRatio = (double)(initialBudget - remaining) / initialBudget;

        log.printf("[summary] round=%d  pts=%d  spent=%d  eff=%.3f  remaining=%d  spent%%=%.1f  wins=%d  losses=%d%n",
            round, actualPts, spent, winEff, remaining, spendRatio * 100, windowWins, windowLosses);

        adaptBidding(actualPts, log);

        windowWins   = 0;
        windowLosses = 0;
        windowBids   = 0;
    }

    private void adaptBidding(long actualPts, PrintStream log) {
        if (windowWins > 0 && windowEstimatedPoints > 0) {
            double currentWindowRatio = (double) actualPts / windowEstimatedPoints;
            // Exponential moving average to smooth out noise
            globalCorrection = (globalCorrection * 0.7) + (currentWindowRatio * 0.3);
        }
        windowEstimatedPoints = 0; // Reset for next window

        boolean floorCleared = totalSpent >= spendFloor;
        
        if (floorCleared) {
            double overallEff = totalSpent > 0 ? (double) totalPoints / totalSpent : 1.0;
            
            effTarget = Math.max(1.0, overallEff * 1.10); 
            log.printf("[phase2] Floor hit! Strict Scavenger Mode. effTarget=%.3f%n", effTarget);
        } else {
            effTarget = 0.42; 
        }

        log.printf("[adjust] correctionFactor=%.3f  newEffTarget=%.3f%n", globalCorrection, effTarget);
    }

    private void onResult(String line, PrintStream log) {
        if (line.charAt(0) == 'W') {
            long cost = Long.parseLong(line.substring(2).trim());
            remaining -= cost;
            windowWins++;
            
            if (lastDidBid) {
                windowEstimatedPoints += lastEstValForWin;
            }
        } else {
            windowLosses++;
        }
        lastDidBid = false;
    }

    private void onBid(String line, PrintStream out, PrintStream log) {
        round++;

        // Parse fields
        int    vidCat      = 0;
        long   viewCount   = 0;
        long   commentCount= 0;
        boolean subscribed = false;
        int    ageIdx      = 2;  
        boolean isMale     = true;
        int[]  interests   = new int[0];

        int start = 0;
        int len   = line.length();
        while (start < len) {
            int comma = line.indexOf(',', start);
            int end   = (comma < 0) ? len : comma;
            int eq    = line.indexOf('=', start);
            if (eq > 0 && eq < end) {
                String key = line.substring(start, eq);
                String val = line.substring(eq + 1, end);
                switch (key) {
                    case "video.category":     vidCat       = catIdx(val);             break;
                    case "video.viewCount":    viewCount    = Long.parseLong(val);      break;
                    case "video.commentCount": commentCount = Long.parseLong(val);      break;
                    case "viewer.subscribed":  subscribed   = val.charAt(0) == 'Y';    break;
                    case "viewer.age":         ageIdx       = ageIdx(val);             break;
                    case "viewer.gender":      isMale       = val.charAt(0) == 'M';    break;
                    case "viewer.interests":   interests    = parseInterests(val);      break;
                }
            }
            start = end + 1;
        }

        int estVal = estimateValue(vidCat, viewCount, commentCount,
                                   subscribed, ageIdx, isMale, interests);
        
        int correctedVal = (int) Math.max(1, estVal * globalCorrection);

        long maxWillingToPay = (long) (correctedVal / effTarget);

        long startBid, maxBid;
        if (remaining <= 0 || maxWillingToPay < 1) {
            startBid = 0L;
            maxBid   = 0L;
            lastDidBid = false;
        } else {
            windowBids++;
            
            startBid = 1L;
            
            // maxBid logic: bounded by budget. 
            // round % 3 adds 0-2 micro-ebucks of noise to naturally bypass exact ties 
            // against other bots estimating the exact same value.
            maxBid = Math.min(maxWillingToPay + (round % 3), remaining);
            
            lastDidBid = true;
            lastEstValForWin = correctedVal;
        }

        out.println(startBid + " " + maxBid);
    }

    // Value estimation (mirrors base harness formula)

    int estimateValue(int vidCat, long viewCount, long commentCount,
                      boolean subscribed, int ageIdx, boolean isMale, int[] interests) {

        int baseValue = 21; 
        for (long[] b : VIEW_BRACKETS) {
            if (viewCount >= b[0] && viewCount <= b[1]) {
                baseValue = (int) b[2];
                break;
            }
        }

        double commentBoost = (9_999.0 + commentCount * 15.0) / (viewCount + 9_999.0);
        double base = baseValue * (1.0 + commentBoost);

        double viewerMul = 0.0;
        if (subscribed) viewerMul += 0.17;

        for (int i = 0; i < interests.length && i < INT_WEIGHTS.length; i++) {
            viewerMul += CAT_MATCH[interests[i]][vidCat] * INT_WEIGHTS[i];
        }

        double[][] ageMult = isMale ? AGE_MALE : AGE_FEMALE;
        if (ageIdx >= 0 && ageIdx < ageMult.length) {
            viewerMul += ageMult[ageIdx][vidCat];
        }

        double adMatch = Math.max(0.161, CAT_MATCH[BOT_CAT][vidCat]);
        return (int) Math.ceil(base * viewerMul * adMatch);
    }

    // Parsing helpers

    private int catIdx(String name) {
        for (int i = 0; i < CAT_NAMES.length; i++)
            if (CAT_NAMES[i].equals(name)) return i;
        return 0; // Default to Music if unknown
    }

    private int ageIdx(String name) {
        for (int i = 0; i < AGE_NAMES.length; i++)
            if (AGE_NAMES[i].equals(name)) return i;
        return 2; // Default to 25-34
    }

    private int[] parseInterests(String val) {
        int count = 1;
        for (int i = 0; i < val.length(); i++) if (val.charAt(i) == ';') count++;
        int[] result = new int[count];
        int idx = 0, s = 0;
        for (int i = 0; i <= val.length(); i++) {
            if (i == val.length() || val.charAt(i) == ';') {
                result[idx++] = catIdx(val.substring(s, i));
                s = i + 1;
            }
        }
        return result;
    }
}