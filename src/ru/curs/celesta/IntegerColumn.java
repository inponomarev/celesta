package ru.curs.celesta;

/**
 * Целочисленная колонка.
 * 
 */
public final class IntegerColumn extends Column {
	private Integer defaultvalue;
	private boolean identity;

	public IntegerColumn(Table table, String name) throws ParseException {
		super(table, name);
	}

	@Override
	protected void setDefault(String lexvalue) {
		if (lexvalue == null) {
			defaultvalue = null;
			identity = false;
		} else if ("IDENTITY".equalsIgnoreCase(lexvalue)) {
			defaultvalue = null;
			identity = true;
		} else {
			defaultvalue = Integer.parseInt(lexvalue);
			identity = false;
		}
	}

	public Integer getDefaultvalue() {
		return defaultvalue;
	}

	public boolean isIdentity() {
		return identity;
	}
}
