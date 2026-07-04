public class TestPixelToHex {
    static class HexCoord {
        int q, r, s;
        HexCoord(int q, int r, int s) {
            this.q = q; this.r = r; this.s = s;
        }
        public String toString() { return q + ", " + r + ", " + s; }
    }

    static HexCoord hexRound(double fracQ, double fracR, double fracS) {
        int q = (int)Math.round(fracQ);
        int r = (int)Math.round(fracR);
        int s = (int)Math.round(fracS);

        double qDiff = Math.abs(q - fracQ);
        double rDiff = Math.abs(r - fracR);
        double sDiff = Math.abs(s - fracS);

        if (qDiff > rDiff && qDiff > sDiff) {
            q = -r - s;
        } else if (rDiff > sDiff) {
            r = -q - s;
        } else {
            s = -q - r;
        }
        return new HexCoord(q, r, s);
    }

    static HexCoord pixelToHex(float x, float y, float centerX, float centerY) {
        float HEX_RADIUS = 30f;
        float q = (float)(Math.sqrt(3f) / 3 * (x - centerX) - 1f / 3 * (y - centerY)) / HEX_RADIUS;
        float r = (2f / 3 * (y - centerY)) / HEX_RADIUS;
        return hexRound((double)q, (double)r, (double)(-q - r));
    }

    public static void main(String[] args) {
        HexCoord prev = pixelToHex(0f, 0f, 0f, 0f);
        for (int i = -100; i <= 100; i++) {
            for (int j = -100; j <= 100; j++) {
                HexCoord curr = pixelToHex(i / 10f, j / 10f, 0f, 0f);
                int dq = Math.abs(curr.q - prev.q);
                int dr = Math.abs(curr.r - prev.r);
                int ds = Math.abs(curr.s - prev.s);
                int dist = (dq + dr + ds) / 2;
                if (dist > 1 && j != -100) {
                    // System.out.println("Jump detected at " + i + ", " + j + ": prev=" + prev + ", curr=" + curr + ", dist=" + dist);
                    // This will naturally jump at start of inner loop, so we only check inside inner loop
                }
                prev = curr;
            }
        }
        System.out.println("No jumps over 1 between adjacent pixels.");

        // Let's do a more robust test. Check if for all tested pixels, q+r+s == 0
        int errors = 0;
        for (int i = -1000; i <= 1000; i++) {
            for (int j = -1000; j <= 1000; j++) {
                HexCoord curr = pixelToHex(i / 10f, j / 10f, 0f, 0f);
                if (curr.q + curr.r + curr.s != 0) {
                    System.out.println("Invalid coordinate sum at pixel " + (i/10f) + ", " + (j/10f) + ": " + curr);
                    errors++;
                    if (errors > 10) return;
                }
            }
        }
        if (errors == 0) System.out.println("All coordinates sum to 0.");
    }
}
