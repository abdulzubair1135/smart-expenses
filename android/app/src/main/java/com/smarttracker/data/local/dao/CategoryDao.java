package com.smarttracker.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.smarttracker.data.local.entity.CategoryEntity;

import java.util.List;

@Dao
public interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CategoryEntity category);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CategoryEntity> categories);

    @Query("SELECT * FROM categories WHERE userId IS NULL OR userId = :userId ORDER BY name ASC")
    LiveData<List<CategoryEntity>> getCategoriesLive(String userId);

    @Query("SELECT * FROM categories WHERE userId IS NULL OR userId = :userId ORDER BY name ASC")
    List<CategoryEntity> getCategories(String userId);
}
