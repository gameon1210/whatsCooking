package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.entity.Member
import kotlinx.coroutines.flow.Flow

interface MemberRepository {
    fun observeActiveMembers(): Flow<List<Member>>
    suspend fun getActiveMembers(): List<Member>
    suspend fun addMember(member: Member): Long
    suspend fun updateMember(member: Member)
    suspend fun deactivateMember(id: Long)
}
