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
				// If both chunks contain numeric characters, sort them numerically
				long n1 = c1 - '0';
				long n2 = c2 - '0';
				int p1 = pos1++;
				int p2 = pos2++;
				while (pos1 < len1) {
					c1 = s1.charAt(pos1);
					if (c1 < '0' || c1 > '9')
						break;
					pos1++;
					n1 = n1 * 10 + (c1 - '0');
				}
				while (pos2 < len2) {
					c2 = s2.charAt(pos2);
					if (c2 < '0' || c2 > '9')
						break;
					pos2++;
					n2 = n2 * 10 + (c2 - '0');
				}
				if (n1 == n2) {
					// Same number value, check with is longer (more leading zeros, Microsoft Windows's sort mode)
					int sub1 = pos1 - p1;
					int sub2 = pos2 - p2;
					result = sub1 == sub2 ? 0 : sub2 - sub1;
				} else {
					result = n1 < n2 ? -1 : 1;
				}
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
