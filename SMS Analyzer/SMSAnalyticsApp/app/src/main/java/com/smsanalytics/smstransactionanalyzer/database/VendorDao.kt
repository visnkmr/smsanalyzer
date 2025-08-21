package com.smsanalytics.smstransactionanalyzer.database

import androidx.room.*
import com.smsanalytics.smstransactionanalyzer.model.Vendor
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorDao {

    @Query("SELECT * FROM vendors ORDER BY name ASC")
    fun getAllVendors(): Flow<List<Vendor>>

    @Query("SELECT * FROM vendors WHERE isExcluded = 0 ORDER BY name ASC")
    fun getActiveVendors(): Flow<List<Vendor>>

    @Query("SELECT * FROM vendors WHERE isExcluded = 1 ORDER BY name ASC")
    fun getExcludedVendors(): Flow<List<Vendor>>

    @Query("SELECT * FROM vendors WHERE name LIKE '%' || :search || '%' ORDER BY name ASC")
    fun searchVendors(search: String): Flow<List<Vendor>>

    @Query("SELECT * FROM vendors WHERE id = :id")
    suspend fun getVendorById(id: Long): Vendor?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendor(vendor: Vendor): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendors(vendors: List<Vendor>)

    @Update
    suspend fun updateVendor(vendor: Vendor)

    @Delete
    suspend fun deleteVendor(vendor: Vendor)

    @Query("UPDATE vendors SET isExcluded = :isExcluded WHERE id = :vendorId")
    suspend fun updateVendorExclusion(vendorId: Long, isExcluded: Boolean)

    @Query("DELETE FROM vendors")
    suspend fun deleteAllVendors()
}