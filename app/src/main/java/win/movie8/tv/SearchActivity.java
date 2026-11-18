package win.movie8.tv;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.recyclerview.widget.GridLayoutManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * TV-style search: an on-screen A-Z keyboard driven entirely by the remote.
 * Typing pinyin initials instantly lists matching Chinese titles from the local
 * pool (history + cached lists); selecting one runs the remote keyword search.
 * The remote API only accepts Chinese keywords, so initial resolution is local.
 */
public final class SearchActivity extends RemoteActivity {
    private final ExecutorService searches=Executors.newSingleThreadExecutor();
    private final SearchRequests resultRequests=new SearchRequests();
    private Future<?> searchTask;
    private MovieAdapter adapter;
    private NavigationBar navigation;
    private TextView queryDisplay;
    private Button searchButton;
    private DpadGridView grid;
    private LinearLayout suggestions;
    private HorizontalScrollView suggestionScroll;
    private LinearLayout historyPanel;
    private GridLayout historyCards;
    private ScrollView historyScroll;
    private ImageButton clearHistory;
    private ProgressBar spinner;
    private TextView status;
    private LinearLayout keyboard;
    private final StringBuilder letters=new StringBuilder();
    private List<String> localPool=new ArrayList<>();
    private long lastBack;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10),dp(4),dp(10),0);root.setBackgroundColor(Color.rgb(16,16,16));root.setClipChildren(false);
        navigation=new NavigationBar(this,"search",route->{
            if("search".equals(route)){focusKeyboardFirst();}
            else NavigationBar.open(this,route,!getWindow().getDecorView().isInTouchMode());
        });
        root.addView(navigation,new LinearLayout.LayoutParams(-1,dp(48)));

        // Query display row: shows typed letters or the resolved Chinese term.
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        queryDisplay=new TextView(this);queryDisplay.setId(View.generateViewId());
        queryDisplay.setTextColor(Color.rgb(246,168,0));queryDisplay.setTextSize(20);queryDisplay.setTypeface(Typeface.DEFAULT_BOLD);
        queryDisplay.setSingleLine(true);queryDisplay.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        queryDisplay.setPadding(dp(12),0,dp(8),0);queryDisplay.setBackgroundResource(R.drawable.focus_button);
        queryDisplay.setFocusable(true);queryDisplay.setFocusableInTouchMode(false);
        queryDisplay.setText(getString(R.string.search_hint_letters));
        bar.addView(queryDisplay,new LinearLayout.LayoutParams(0,dp(48),1));
        searchButton=button(getString(R.string.search));searchButton.setId(View.generateViewId());
        bar.addView(searchButton,new LinearLayout.LayoutParams(dp(110),dp(48)));
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(52)));
        navigation.setDownTarget(queryDisplay.getId());
        queryDisplay.setNextFocusUpId(navigation.buttons.findViewWithTag("search").getId());

        buildHistory(root);

        // Live suggestion strip (Chinese titles from the local pool).
        suggestionScroll=new HorizontalScrollView(this);suggestionScroll.setHorizontalScrollBarEnabled(false);suggestionScroll.setFocusable(false);
        suggestions=new LinearLayout(this);suggestions.setOrientation(LinearLayout.HORIZONTAL);
        suggestionScroll.addView(suggestions);suggestionScroll.setVisibility(View.GONE);
        root.addView(suggestionScroll,new LinearLayout.LayoutParams(-1,dp(52)));

        status=new TextView(this);status.setTextColor(Color.LTGRAY);status.setTextSize(15);status.setVisibility(View.GONE);
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));

        // Results grid.
        FrameLayout results=new FrameLayout(this);
        grid=new DpadGridView(this);grid.setId(View.generateViewId());grid.setPadding(0,dp(4),0,0);
        grid.setLayoutManager(new GridLayoutManager(this,5));grid.setContainHorizontalFocus(true);
        adapter=new MovieAdapter(this,this::open);grid.setAdapter(adapter);
        grid.setOnTopRowUpListener(()->{if(suggestionScroll.getVisibility()==View.VISIBLE&&suggestions.getChildCount()>0)suggestions.getChildAt(0).requestFocus();else searchButton.requestFocus();});
        results.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        spinner=new ProgressBar(this);spinner.setIndeterminate(true);spinner.setFocusable(false);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.rgb(246,168,0)));
        spinner.setContentDescription(getString(R.string.searching));spinner.setVisibility(View.GONE);
        results.addView(spinner,new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.CENTER));
        root.addView(results,new LinearLayout.LayoutParams(-1,0,1));

        // On-screen A-Z keyboard at the bottom.
        keyboard=buildKeyboard();
        root.addView(keyboard,new LinearLayout.LayoutParams(-1,-2));

        setContentView(root);
        localPool=LocalTitleIndex.titles(this);
        searchButton.setOnClickListener(view->submitQuery());
        queryDisplay.setOnKeyListener((view,key,event)->{
            if(event.getAction()==KeyEvent.ACTION_DOWN && key==KeyEvent.KEYCODE_DPAD_DOWN){
                if(suggestionScroll.getVisibility()==View.VISIBLE&&suggestions.getChildCount()>0)suggestions.getChildAt(0).requestFocus();
                else if(keyboard.getChildCount()>0)keyboard.getChildAt(0).findViewWithTag("k_A").requestFocus();
                return true;
            }
            return false;
        });
        renderHistory();
        // When arriving via the remote from the home nav bar, keep focus on the nav bar
        // (DOWN then walks into the query row and keyboard). Otherwise focus the keyboard.
        if(getIntent().getBooleanExtra("navigation_focus",false)){
            navigation.post(navigation::focusCurrent);
        }else{
            keyboard.post(()->{
                View first=keyboard.findViewWithTag("k_A");
                if(first!=null)first.requestFocus();
                else keyboard.post(this::focusKeyboardFirst);
            });
        }
    }

    /** Builds a 4-row A-Z keyboard plus Delete and Clear. Each key is remote-navigable. */
    private LinearLayout buildKeyboard(){
        LinearLayout container=new LinearLayout(this);container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0,dp(6),0,dp(4));
        String[] rows={"ABCDEFG","HIJKLMN","OPQRSTU","VWXYZ"};
        int[] cols={7,7,7,5};
        for(int r=0;r<rows.length;r++){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
            String lettersRow=rows[r];
            for(int c=0;c<lettersRow.length();c++){
                final String ch=String.valueOf(lettersRow.charAt(c));
                Button key=keyButton(ch,"k_"+ch);
                key.setOnClickListener(view->typeLetter(ch));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(116),dp(46));p.setMargins(dp(3),0,dp(3),dp(3));
                row.addView(key,p);
            }
            if(r==3){
                Button del=keyButton("⌫","k_del");del.setOnClickListener(view->deleteLetter());
                Button clear=keyButton(getString(R.string.clear),"k_clear");clear.setOnClickListener(view->clearQuery());
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(150),dp(46));p.setMargins(dp(3),0,dp(3),dp(3));
                row.addView(del,p);row.addView(clear,p);
            }
            container.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        // Explicit focus navigation across the keyboard grid.
        for(int r=0;r<container.getChildCount();r++){
            LinearLayout row=(LinearLayout)container.getChildAt(r);
            for(int c=0;c<row.getChildCount();c++){
                View key=row.getChildAt(c);
                if(c>0)key.setNextFocusLeftId(row.getChildAt(c-1).getId());
                if(c<row.getChildCount()-1)key.setNextFocusRightId(row.getChildAt(c+1).getId());
                if(r>0){LinearLayout above=(LinearLayout)container.getChildAt(r-1);key.setNextFocusUpId(above.getChildAt(Math.min(c,above.getChildCount()-1)).getId());}
                if(r<container.getChildCount()-1){LinearLayout below=(LinearLayout)container.getChildAt(r+1);key.setNextFocusDownId(below.getChildAt(Math.min(c,below.getChildCount()-1)).getId());}
                key.setNextFocusUpId(key.getNextFocusUpId());
            }
        }
        // Last row's Down returns to suggestions/query; first row's Up to query.
        LinearLayout first=(LinearLayout)container.getChildAt(0);
        for(int c=0;c<first.getChildCount();c++)first.getChildAt(c).setNextFocusUpId(queryDisplay.getId());
        return container;
    }

    private Button keyButton(String label,String tag){
        Button b=new Button(this);b.setId(View.generateViewId());b.setTag(tag);b.setText(label);
        b.setTextColor(Color.WHITE);b.setTextSize(15);b.setAllCaps(false);b.setGravity(Gravity.CENTER);
        b.setMinWidth(0);b.setMinHeight(0);b.setMinimumWidth(0);b.setMinimumHeight(0);
        b.setFocusable(true);b.setFocusableInTouchMode(false);b.setBackgroundResource(R.drawable.focus_button);
        return b;
    }

    private void focusKeyboardFirst(){
        View first=keyboard==null?null:keyboard.findViewWithTag("k_A");
        if(first!=null)first.requestFocus();
    }

    private void typeLetter(String ch){
        letters.append(ch.toLowerCase(java.util.Locale.ROOT));
        queryDisplay.setText(letters.toString());
        refreshSuggestions();
    }

    private void deleteLetter(){
        if(letters.length()==0)return;
        letters.deleteCharAt(letters.length()-1);
        queryDisplay.setText(letters.length()==0?getString(R.string.search_hint_letters):letters.toString());
        refreshSuggestions();
    }

    private void clearQuery(){
        letters.setLength(0);
        queryDisplay.setText(getString(R.string.search_hint_letters));
        suggestions.removeAllViews();suggestionScroll.setVisibility(View.GONE);
        adapter.replace(java.util.Collections.emptyList());status.setVisibility(View.GONE);
        renderHistory();
        focusKeyboardFirst();
    }

    /** Instantly lists local titles matching the typed initials (no network). */
    private void refreshSuggestions(){
        String query=letters.toString().trim();
        suggestions.removeAllViews();
        if(query.isEmpty()){suggestionScroll.setVisibility(View.GONE);renderHistory();return;}
        List<String> matches=LocalTitleIndex.suggest(localPool,query);
        historyPanel.setVisibility(View.GONE);
        if(matches.isEmpty()){suggestionScroll.setVisibility(View.GONE);return;}
        for(int i=0;i<matches.size();i++){
            final String title=matches.get(i);
            Button item=button(title);item.setId(View.generateViewId());item.setTextSize(14);
            item.setSingleLine(true);item.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.setOnClickListener(view->submit(title));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(210),dp(40));p.setMargins(dp(3),0,dp(3),0);
            suggestions.addView(item,p);
        }
        for(int i=0;i<suggestions.getChildCount();i++){
            View item=suggestions.getChildAt(i);
            if(i>0)item.setNextFocusLeftId(suggestions.getChildAt(i-1).getId());
            if(i<suggestions.getChildCount()-1)item.setNextFocusRightId(suggestions.getChildAt(i+1).getId());
            item.setNextFocusUpId(queryDisplay.getId());
            item.setNextFocusDownId(searchButton.getId());
        }
        suggestionScroll.setVisibility(View.VISIBLE);
    }

    /** Search using the typed letters directly (works for Latin titles / literal keywords). */
    private void submitQuery(){
        String query=letters.toString().trim();
        if(query.isEmpty()){
            if(suggestions.getChildCount()>0)suggestions.getChildAt(0).performClick();
            return;
        }
        submit(query);
    }

    /** Runs the remote keyword search for a resolved Chinese title or a literal query. */
    private void submit(final String query){
        if(query.isEmpty())return;
        SearchHistory.record(this,query);
        localPool=LocalTitleIndex.titles(this);
        letters.setLength(0);
        queryDisplay.setText(query);
        suggestions.removeAllViews();suggestionScroll.setVisibility(View.GONE);
        historyPanel.setVisibility(View.GONE);
        searchButton.requestFocus();
        final int request=resultRequests.next();
        if(searchTask!=null)searchTask.cancel(true);
        adapter.replace(java.util.Collections.emptyList());status.setVisibility(View.GONE);spinner.setVisibility(View.VISIBLE);
        searchTask=searches.submit(()->{
            try{
                List<Movie> data=WencaiClient.search(query);
                runOnUiThread(()->{
                    if(isDestroyed()||!resultRequests.accepts(request))return;
                    spinner.setVisibility(View.GONE);adapter.replace(data);
                    if(data.isEmpty()){status.setText(R.string.no_search_results);status.setVisibility(View.VISIBLE);}
                    else grid.post(()->{if(resultRequests.accepts(request)&&searchButton.hasFocus()&&hasWindowFocus())focusResults();});
                });
            }catch(Exception error){runOnUiThread(()->{
                if(isDestroyed()||!resultRequests.accepts(request))return;
                spinner.setVisibility(View.GONE);status.setText(getString(R.string.load_failed,error.getMessage()));status.setVisibility(View.VISIBLE);
            });}
        });
    }

    private void buildHistory(LinearLayout root){
        historyPanel=new LinearLayout(this);historyPanel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);title.setText(R.string.search_history);title.setTextColor(Color.LTGRAY);title.setTextSize(14);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(34),1));
        clearHistory=new ImageButton(this);clearHistory.setId(View.generateViewId());clearHistory.setImageResource(R.drawable.ic_delete);
        clearHistory.setContentDescription(getString(R.string.clear_search_history));clearHistory.setBackgroundResource(R.drawable.focus_button);
        clearHistory.setNextFocusUpId(queryDisplay.getId());header.addView(clearHistory,new LinearLayout.LayoutParams(dp(44),dp(34)));
        historyPanel.addView(header);historyScroll=new ScrollView(this);historyScroll.setFocusable(false);historyCards=new GridLayout(this);
        historyScroll.addView(historyCards);historyPanel.addView(historyScroll,new LinearLayout.LayoutParams(-1,dp(40)));
        root.addView(historyPanel,new LinearLayout.LayoutParams(-1,-2));
        clearHistory.setOnClickListener(view->new android.app.AlertDialog.Builder(this).setMessage(R.string.confirm_clear_search_history).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.clear,(dialog,which)->{SearchHistory.clear(this);renderHistory();focusKeyboardFirst();}).show());
    }

    private void renderHistory(){
        List<String> terms=SearchHistory.items(this);historyCards.removeAllViews();
        historyPanel.setVisibility(terms.isEmpty()||letters.length()>0?View.GONE:View.VISIBLE);
        int columns=Math.max(2,Math.min(6,getResources().getDisplayMetrics().widthPixels/dp(180)));
        historyCards.setColumnCount(columns);
        for(int i=0;i<terms.size();i++){
            final String term=terms.get(i);Button card=button(term);card.setId(View.generateViewId());card.setTextSize(14);
            card.setSingleLine(true);card.setEllipsize(android.text.TextUtils.TruncateAt.END);
            GridLayout.LayoutParams params=new GridLayout.LayoutParams();params.width=0;params.height=dp(40);
            params.rowSpec=GridLayout.spec(i/columns);params.columnSpec=GridLayout.spec(i%columns,GridLayout.FILL,1f);params.setMargins(dp(2),dp(2),dp(2),dp(2));
            historyCards.addView(card,params);
            card.setOnClickListener(view->submit(term));
            card.setOnLongClickListener(view->{new android.app.AlertDialog.Builder(this).setMessage(R.string.confirm_delete_search_history).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.delete,(dialog,which)->{SearchHistory.delete(this,term);renderHistory();if(historyCards.getChildCount()>0)historyCards.getChildAt(0).requestFocus();else focusKeyboardFirst();}).show();return true;});
        }
        for(int i=0;i<terms.size();i++){
            View card=historyCards.getChildAt(i);
            card.setNextFocusLeftId(historyCards.getChildAt(HorizontalGridFocus.target(i,terms.size(),columns,-1)).getId());
            card.setNextFocusRightId(historyCards.getChildAt(HorizontalGridFocus.target(i,terms.size(),columns,1)).getId());
            card.setNextFocusUpId(queryDisplay.getId());
            card.setNextFocusDownId(keyboard!=null&&keyboard.getChildCount()>0?keyboard.getChildAt(0).findViewWithTag("k_A").getId():card.getId());
        }
        if(!terms.isEmpty())clearHistory.setNextFocusDownId(historyCards.getChildAt(Math.min(columns,terms.size())-1).getId());
        historyScroll.getLayoutParams().height=dp(40*Math.min(2,Math.max(1,(terms.size()+columns-1)/columns)));
        historyScroll.requestLayout();
    }

    private void focusResults(){androidx.recyclerview.widget.RecyclerView.ViewHolder item=grid.findViewHolderForAdapterPosition(0);if(item!=null)item.itemView.requestFocus();else searchButton.requestFocus();}
    private Button button(String title){Button b=new Button(this);b.setText(title);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setFocusable(true);b.setFocusableInTouchMode(false);b.setBackgroundResource(R.drawable.focus_button);b.setMinHeight(0);b.setMinimumHeight(0);return b;}

    @Override public void onBackPressed(){
        if(letters.length()>0){clearQuery();return;}
        if(suggestionScroll.getVisibility()==View.VISIBLE){suggestions.removeAllViews();suggestionScroll.setVisibility(View.GONE);renderHistory();return;}
        if(adapter.getItemCount()>0){adapter.replace(java.util.Collections.emptyList());grid.scrollTo(0,0);focusKeyboardFirst();return;}
        if(System.currentTimeMillis()-lastBack<1800){super.onBackPressed();return;}
        lastBack=System.currentTimeMillis();
        navigation.focusCurrent();
    }
    @Override protected void onDestroy(){resultRequests.next();if(searchTask!=null)searchTask.cancel(true);searches.shutdownNow();super.onDestroy();}
    private void open(Movie movie){Intent intent=new Intent(this,PlayerActivity.class);intent.putExtra("id",movie.id);intent.putExtra("name",movie.name);intent.putExtra("poster",movie.poster);intent.putExtra("remarks",movie.remarks);startActivity(intent);}
    private int dp(int value){return(int)(value*getResources().getDisplayMetrics().density+.5f);}
}
