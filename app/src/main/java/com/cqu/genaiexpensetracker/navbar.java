package com.cqu.genaiexpensetracker;

import android.os.Bundle;
<<<<<<< HEAD

import androidx.activity.EdgeToEdge;
=======
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
>>>>>>> origin/feature-nav-bar
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
<<<<<<< HEAD

public class navbar extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
=======
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigation.NavigationView;

public class navbar extends AppCompatActivity {
DrawerLayout drawerLayout;
ImageButton buttondrawerToggle;
NavigationView navigationView;
addIncome addIncome=new addIncome();
addExpense addExpense=new addExpense();
profile profile=new profile();
insights insights=new insights();



Dashboard dashboard=new Dashboard();
Income income=new Income();
Expense expense=new Expense();
BottomNavigationView bottomNavigationView;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navbar);


        drawerLayout=findViewById(R.id.drawer_layout);
        buttondrawerToggle=findViewById(R.id.buttondrawertoogle);
        navigationView=findViewById(R.id.navigation_view);

        buttondrawerToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawerLayout.open();
            }
        });
navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
      int itemid=item.getItemId();

       if (itemid==R.id.income_menu){
           getSupportFragmentManager().beginTransaction().replace(R.id.main_frame, addIncome).commit();
drawerLayout.close();
        }
      else if (itemid==R.id.expense_menu){
           getSupportFragmentManager().beginTransaction().replace(R.id.main_frame,addExpense).commit();
           drawerLayout.close();

      }
      else if (itemid==R.id.insights_menu){
           getSupportFragmentManager().beginTransaction().replace(R.id.main_frame,profile).commit();
           drawerLayout.close();
      }
      else if (itemid==R.id.profile_menu){

           getSupportFragmentManager().beginTransaction().replace(R.id.main_frame,insights).commit();
           drawerLayout.close();
      }


        return false;


    }
});
//bottom navigation

        bottomNavigationView=findViewById(R.id.bottom_navigation);
        getSupportFragmentManager().beginTransaction().replace(R.id.main_frame,dashboard).commit();

bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        int id = item.getItemId();
      if(id==R.id.navigation_dashboard){
          getSupportFragmentManager().beginTransaction().replace(R.id.main_frame,dashboard).commit();

      }
      else if(id==R.id.navigation_income){
          getSupportFragmentManager().beginTransaction().replace(R.id.main_frame,income).commit();
      }

      else if(id==R.id.navigation_expense){
          getSupportFragmentManager().beginTransaction().replace(R.id.main_frame,expense).commit();
      }
        return false;
    }
});




>>>>>>> origin/feature-nav-bar
    }
}