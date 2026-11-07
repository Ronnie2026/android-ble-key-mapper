package com.yijia.buttons;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.text.Collator;
import java.util.*;

/** Searchable app selector. Internal package identifiers are never displayed. */
final class AppPicker {
  interface Result {
    void selected(List<String> packages);
  }

  private static class App {
    String name, pkg;
    Drawable icon;
  }

  static String name(Context c, String pkg) {
    try {
      return c.getPackageManager()
          .getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0))
          .toString();
    } catch (Exception e) {
      return "已卸载的应用";
    }
  }

  static String summary(Context c, List<String> packages) {
    List<String> names = new ArrayList<>();
    for (String pkg : packages) names.add(name(c, pkg));
    return names.isEmpty() ? "尚未选择应用" : String.join("、", names);
  }

  static String normalized(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]", "");
  }

  static void show(Activity activity, List<String> initial, Result result) {
    PackageManager manager = activity.getPackageManager();
    List<App> apps = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (ResolveInfo r :
        manager.queryIntentActivities(
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
      String pkg = r.activityInfo.packageName;
      if (pkg.equals(activity.getPackageName()) || !seen.add(pkg)) continue;
      App app = new App();
      app.pkg = pkg;
      app.name = r.loadLabel(manager).toString();
      app.icon = r.loadIcon(manager);
      apps.add(app);
    }
    Collator collator = Collator.getInstance(Locale.getDefault());
    apps.sort((a, b) -> collator.compare(a.name, b.name));
    LinkedHashSet<String> selected = new LinkedHashSet<>(initial);
    selected.retainAll(seen);
    apps.sort(
        (a, b) ->
            selected.contains(a.pkg) == selected.contains(b.pkg)
                ? collator.compare(a.name, b.name)
                : (selected.contains(a.pkg) ? -1 : 1));
    List<App> filtered = new ArrayList<>(apps);
    LinearLayout content = Ui.column(activity);
    content.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 6), Ui.dp(activity, 16), 0);
    EditText search = new EditText(activity);
    search.setSingleLine();
    search.setHint("搜索应用名称");
    search.setTextSize(15);
    content.addView(search, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 50)));
    TextView count = Ui.note(content, "");
    count.setMaxLines(2);
    count.setEllipsize(android.text.TextUtils.TruncateAt.END);
    ListView list = new ListView(activity);
    list.setDividerHeight(0);
    content.addView(
        list,
        new LinearLayout.LayoutParams(
            -1,
            Math.min(
                Ui.dp(activity, 345),
                activity.getResources().getDisplayMetrics().heightPixels / 2)));
    Ui.note(content, "想打开几个应用，就选几个。多个应用会依次打开，最后一个显示在前台。");
    ViewTreeObserver.OnGlobalLayoutListener resizeForKeyboard =
        () -> {
          android.graphics.Rect visible = new android.graphics.Rect();
          content.getWindowVisibleDisplayFrame(visible);
          int height =
              Math.max(
                  Ui.dp(activity, 100),
                  Math.min(Ui.dp(activity, 345), visible.height() - Ui.dp(activity, 285)));
          if (list.getLayoutParams().height != height) {
            list.getLayoutParams().height = height;
            list.requestLayout();
          }
        };
    content.getViewTreeObserver().addOnGlobalLayoutListener(resizeForKeyboard);
    BaseAdapter adapter =
        new BaseAdapter() {
          public int getCount() {
            return filtered.size();
          }

          public Object getItem(int position) {
            return filtered.get(position);
          }

          public long getItemId(int position) {
            return position;
          }

          public View getView(int position, View convert, ViewGroup parent) {
            App app = filtered.get(position);
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(
                Ui.dp(activity, 10), Ui.dp(activity, 9), Ui.dp(activity, 8), Ui.dp(activity, 9));
            if (selected.contains(app.pkg)) row.setBackground(Ui.background(activity, Ui.TINT, 14));
            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(app.icon);
            row.addView(
                icon, new LinearLayout.LayoutParams(Ui.dp(activity, 38), Ui.dp(activity, 38)));
            LinearLayout labels = Ui.column(activity);
            labels.setPadding(Ui.dp(activity, 12), 0, 0, 0);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            labels.addView(Ui.text(activity, app.name, 16, Ui.TEXT));
            if (selected.contains(app.pkg))
              labels.addView(
                  Ui.text(
                      activity,
                      "已选 · 第 " + (new ArrayList<>(selected).indexOf(app.pkg) + 1) + " 个打开",
                      12,
                      Ui.ACCENT));
            CheckBox check = new CheckBox(activity);
            check.setChecked(selected.contains(app.pkg));
            check.setClickable(false);
            check.setFocusable(false);
            check.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
            row.addView(check);
            row.setContentDescription(app.name + (selected.contains(app.pkg) ? "，已选择" : "，未选择"));
            return row;
          }
        };
    list.setAdapter(adapter);
    AlertDialog dialog =
        new AlertDialog.Builder(activity)
            .setTitle("选择应用")
            .setView(content)
            .setNegativeButton("取消", null)
            .setPositiveButton("选好了", null)
            .create();
    Runnable update =
        () -> {
          count.setText(
              filtered.isEmpty()
                  ? "没有找到应用，试试其他名称"
                  : "已选 " + selected.size() + " 个：" + summary(activity, new ArrayList<>(selected)));
          if (dialog.isShowing())
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("选好了（" + selected.size() + "）");
          adapter.notifyDataSetChanged();
        };
    list.setOnItemClickListener(
        (parent, view, position, id) -> {
          String pkg = filtered.get(position).pkg;
          if (!selected.remove(pkg)) selected.add(pkg);
          update.run();
        });
    list.setOnItemLongClickListener(
        (parent, view, position, id) -> {
          activity.startActivity(
              new Intent(
                  android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                  android.net.Uri.parse("package:" + filtered.get(position).pkg)));
          return true;
        });
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void afterTextChanged(Editable e) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            String query = normalized(s.toString());
            filtered.clear();
            for (App app : apps) if (normalized(app.name).contains(query)) filtered.add(app);
            update.run();
          }
        });
    dialog.setOnShowListener(
        ignored -> {
          update.run();
          dialog
              .getButton(AlertDialog.BUTTON_POSITIVE)
              .setOnClickListener(
                  v -> {
                    if (selected.isEmpty()) {
                      Toast.makeText(activity, "先选一个喜欢的应用吧", Toast.LENGTH_SHORT).show();
                      return;
                    }
                    result.selected(new ArrayList<>(selected));
                    dialog.dismiss();
                  });
          dialog.getWindow().setBackgroundDrawable(Ui.background(activity, Ui.CARD, 22));
          dialog
              .getWindow()
              .setSoftInputMode(
                  WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                      | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        });
    dialog.setOnDismissListener(
        ignored -> content.getViewTreeObserver().removeOnGlobalLayoutListener(resizeForKeyboard));
    dialog.show();
  }
}
