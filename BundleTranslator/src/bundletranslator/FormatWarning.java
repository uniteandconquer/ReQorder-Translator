package bundletranslator;

public class FormatWarning
{
    protected int index;
        protected int newLines;
        protected int lineTags;
        protected int breaks;
        protected int placeHolders;
        protected int singleQuotes;
        protected int htmlOpen;
        protected int htmlClose;
        
        public FormatWarning(int index,int newLines,int lineTags,int breaks,int placeHolders,int htmlOpen,int htmlClose, int singleQuotes)
        {            
            this.index = index;
            this.newLines = newLines;
            this.lineTags = lineTags;
            this.breaks = breaks;
            this.placeHolders = placeHolders;
            this.htmlOpen = htmlOpen;
            this.htmlClose = htmlClose;
            this.singleQuotes = singleQuotes;
        }
}
