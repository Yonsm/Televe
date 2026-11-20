package io.library.comparator;

public class AlphanumComparator extends StringComparator {
	@Override
	public int compare(String s1, String s2) {
		final int len1 = s1 != null ? s1.length() : 0;
		final int len2 = s2 != null ? s2.length() : 0;
		int pos1 = 0;
		int pos2 = 0;

		while (pos1 < len1 && pos2 < len2) {
			char c1 = s1.charAt(pos1);
			char c2 = s2.charAt(pos2);
			boolean d1 = c1 >= '0' && c1 <= '9';
			boolean d2 = c2 >= '0' && c2 <= '9';
			int result;

			if (d1 && d2) {
				// Compare numeric chunks without long overflow
				int p1 = pos1, p2 = pos2;
				while (pos1 < len1 && s1.charAt(pos1) == '0') pos1++;
				while (pos2 < len2 && s2.charAt(pos2) == '0') pos2++;
				int nz1 = pos1, nz2 = pos2;
				while (pos1 < len1 && s1.charAt(pos1) >= '0' && s1.charAt(pos1) <= '9') pos1++;
				while (pos2 < len2 && s2.charAt(pos2) >= '0' && s2.charAt(pos2) <= '9') pos2++;
				result = (pos1 - nz1) - (pos2 - nz2);
				for (int i = 0; result == 0 && i < pos1 - nz1; i++)
					result = s1.charAt(nz1 + i) - s2.charAt(nz2 + i);
				if (result == 0) result = (pos2 - p2) - (pos1 - p1);
			} else if (c1 == c2) {
				// Equals
				result = 0;
				pos1++;
				pos2++;
			} else {
				// No any digit, sort by local
				result = super.compare(s1.substring(pos1), s2.substring(pos2));//c1 - c2;
				pos1++;
				pos2++;
			}

			if (result != 0)
				return result;
		}

		return len1 - len2;
	}
}
