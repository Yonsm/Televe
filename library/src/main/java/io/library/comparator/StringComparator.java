package io.library.comparator;

import java.text.Collator;
import java.util.Comparator;

public class StringComparator implements Comparator<String> {
	private static final Collator mComparator = Collator.getInstance();

	@Override
	public int compare(String s1, String s2) {
		int ret = mComparator.compare(s1, s2);
		if (ret == 0 && s1 != null) // The local collator will return 0 if they have same meaning
			ret = s1.compareTo(s2);
		return ret;
	}
}
