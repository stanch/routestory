import scalariform.formatter.preferences._

scalariformSettings

ScalariformKeys.preferences := FormattingPreferences()
.setPreference(RewriteArrowSymbols, true)
.setPreference(PreserveDanglingCloseParenthesis, true)