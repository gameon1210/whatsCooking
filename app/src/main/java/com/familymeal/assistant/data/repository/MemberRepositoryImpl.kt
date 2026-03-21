package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.dao.MemberDao
import com.familymeal.assistant.data.db.entity.Member
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MemberRepositoryImpl @Inject constructor(
    private val memberDao: MemberDao
) : MemberRepository {
    override fun observeActiveMembers() = memberDao.observeActiveMembers()
    override suspend fun getActiveMembers() = memberDao.getActiveMembers()
    override suspend fun addMember(member: Member) = memberDao.insertMember(member)
    override suspend fun updateMember(member: Member) = memberDao.updateMember(member)
    override suspend fun deactivateMember(id: Long) = memberDao.deactivateMember(id)
}
