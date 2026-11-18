package win.movie8.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

final class NavigationBar extends HorizontalScrollView {
    interface Listener { void open(String route); }
    final LinearLayout buttons;
    private final Activity activity;
    private final Listener listener;
    private String current;
    private final String[] routes={"recent","movie","series","variety","anime","search","history"};
    private final int[] labels={R.string.recent,R.string.movies,R.string.series,R.string.variety,R.string.anime,R.string.search,R.string.history};
    // Compact glyph prefixes give the flat text tabs a TV-style icon affordance without adding assets.
    private final String[] icons={"●","▶","▤","✦","✿","⌕","◷"};

    NavigationBar(Activity activity,String current,Listener listener) {
        super(activity);this.activity=activity;this.current=current;this.listener=listener;
        setHorizontalScrollBarEnabled(false);setClipToPadding(false);setClipChildren(false);
        buttons=new LinearLayout(activity);buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setPadding(dp(12),dp(4),dp(12),dp(4));addView(buttons);
        android.content.res.ColorStateList navText=new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_focused},new int[]{android.R.attr.state_selected},new int[]{}},
                new int[]{Color.rgb(24,20,10),Color.rgb(246,168,0),Color.rgb(224,224,224)});
        for(int i=0;i<routes.length;i++) {
            final String route=routes[i];Button button=new Button(activity);
            button.setId(View.generateViewId());
            button.setTag(route);button.setText(activity.getString(R.string.nav_tab,icons[i],activity.getString(labels[i])));
            button.setTextColor(navText);button.setTextSize(15);button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            button.setAllCaps(false);button.setGravity(Gravity.CENTER);button.setMinWidth(0);button.setMinHeight(0);
            button.setMinimumWidth(0);button.setMinimumHeight(0);button.setPadding(dp(16),0,dp(16),0);
            button.setFocusable(true);button.setFocusableInTouchMode(false);button.setBackgroundResource(R.drawable.nav_button);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,dp(38));params.setMargins(0,0,dp(8),0);
            buttons.addView(button,params);
            Runnable activate=()->{if(button.hasFocus()&&activity.hasWindowFocus()&&!activity.isFinishing()&&!activity.isDestroyed()&&!this.current.equals(route))listener.open(route);};
            button.setOnFocusChangeListener((view,focused)->{
                view.removeCallbacks(activate);
                if(focused)view.postDelayed(activate,180);
            });
            button.setOnClickListener(view->{view.removeCallbacks(activate);listener.open(route);});
        }
        setCurrent(current);
    }
    void setCurrent(String route){current=route;for(int i=0;i<buttons.getChildCount();i++){View button=buttons.getChildAt(i);button.setSelected(route.equals(button.getTag()));}}
    void focusCurrent(){View button=buttons.findViewWithTag(current);if(button!=null)button.requestFocus();}
    void setDownTarget(int id){for(int i=0;i<buttons.getChildCount();i++)buttons.getChildAt(i).setNextFocusDownId(id);}
    static void open(Activity host,String route,boolean remote){
        Class<?> target="search".equals(route)?SearchActivity.class:"history".equals(route)?HistoryActivity.class:MainActivity.class;
        Intent intent=new Intent(host,target).putExtra("section",route).putExtra("navigation_focus",remote);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        host.startActivity(intent);
        if(!(host instanceof MainActivity))host.finish();
    }
    private int dp(int value){return(int)(value*getResources().getDisplayMetrics().density+.5f);}
}
