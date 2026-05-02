import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getSystemBanks,
  getUserBanks,
  getMyBanks,
  getBankById,
  createBank,
  updateBank,
  deleteBank,
  getQuestionsByBankIdPaged,
  getQuestionById,
  createQuestion,
  updateQuestion,
  deleteQuestion,
  type QuestionBankDTO,
  type QuestionDTO,
  type QuestionDifficulty,
  type CreateQuestionBankRequest,
  type CreateQuestionRequest
} from '../api/question-bank'

export const useQuestionBankStore = defineStore('question-bank', () => {
  // ========== State ==========
  const systemBanks = ref<QuestionBankDTO[]>([])
  const userBanks = ref<QuestionBankDTO[]>([])
  const currentBank = ref<QuestionBankDTO | null>(null)
  const questions = ref<QuestionDTO[]>([])
  const currentQuestion = ref<QuestionDTO | null>(null)
  const isLoading = ref(false)

  // 分页信息
  const pagination = ref({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0
  })

  // ========== Actions ==========

  /**
   * 获取所有题库（包含系统题库和用户题库）
   */
  const fetchAllBanks = async () => {
    isLoading.value = true
    try {
      const banks = await getUserBanks()
      systemBanks.value = banks.filter(b => b.type === 'SYSTEM')
      userBanks.value = banks.filter(b => b.type === 'USER')
      return banks
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取系统题库列表
   */
  const fetchSystemBanks = async () => {
    isLoading.value = true
    try {
      const banks = await getSystemBanks()
      systemBanks.value = banks
      return banks
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取我的题库列表
   */
  const fetchMyBanks = async () => {
    isLoading.value = true
    try {
      const banks = await getMyBanks()
      userBanks.value = banks
      return banks
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取题库详情
   */
  const fetchBankDetail = async (bankId: number) => {
    isLoading.value = true
    try {
      const bank = await getBankById(bankId)
      currentBank.value = bank
      return bank
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 创建题库
   */
  const addBank = async (data: CreateQuestionBankRequest) => {
    isLoading.value = true
    try {
      const bank = await createBank(data)
      userBanks.value.push(bank)
      return bank
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 更新题库
   */
  const modifyBank = async (bankId: number, data: CreateQuestionBankRequest) => {
    isLoading.value = true
    try {
      const bank = await updateBank(bankId, data)
      // 更新列表中的数据
      const index = userBanks.value.findIndex(b => b.id === bankId)
      if (index > -1) {
        userBanks.value[index] = bank
      }
      if (currentBank.value?.id === bankId) {
        currentBank.value = bank
      }
      return bank
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 删除题库
   */
  const removeBank = async (bankId: number) => {
    isLoading.value = true
    try {
      await deleteBank(bankId)
      // 从列表中移除
      const index = userBanks.value.findIndex(b => b.id === bankId)
      if (index > -1) {
        userBanks.value.splice(index, 1)
      }
      if (currentBank.value?.id === bankId) {
        currentBank.value = null
      }
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 分页获取题目列表
   */
  const fetchQuestions = async (
    bankId: number,
    page: number = 0,
    difficulty?: QuestionDifficulty,
    keyword?: string
  ) => {
    isLoading.value = true
    try {
      const res = await getQuestionsByBankIdPaged(bankId, page, pagination.value.size, difficulty, keyword)
      questions.value = res.content || []
      pagination.value = {
        page: res.number,
        size: res.size,
        totalElements: res.totalElements,
        totalPages: res.totalPages
      }
      return res
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取题目详情
   */
  const fetchQuestionDetail = async (questionId: number) => {
    isLoading.value = true
    try {
      const question = await getQuestionById(questionId)
      currentQuestion.value = question
      return question
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 创建题目
   */
  const addQuestion = async (data: CreateQuestionRequest) => {
    isLoading.value = true
    try {
      const question = await createQuestion(data)
      questions.value.push(question)
      // 更新题库的题目数量
      if (currentBank.value) {
        currentBank.value.questionCount++
      }
      return question
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 更新题目
   */
  const modifyQuestion = async (questionId: number, data: Partial<CreateQuestionRequest>) => {
    isLoading.value = true
    try {
      const question = await updateQuestion(questionId, data)
      // 更新列表中的数据
      const index = questions.value.findIndex(q => q.id === questionId)
      if (index > -1) {
        questions.value[index] = question
      }
      if (currentQuestion.value?.id === questionId) {
        currentQuestion.value = question
      }
      return question
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 删除题目
   */
  const removeQuestion = async (questionId: number) => {
    isLoading.value = true
    try {
      await deleteQuestion(questionId)
      // 从列表中移除
      const index = questions.value.findIndex(q => q.id === questionId)
      if (index > -1) {
        questions.value.splice(index, 1)
      }
      if (currentQuestion.value?.id === questionId) {
        currentQuestion.value = null
      }
      // 更新题库的题目数量
      if (currentBank.value) {
        currentBank.value.questionCount--
      }
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 重置状态
   */
  const reset = () => {
    currentBank.value = null
    questions.value = []
    currentQuestion.value = null
    isLoading.value = false
    pagination.value = {
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0
    }
  }

  return {
    // State
    systemBanks,
    userBanks,
    currentBank,
    questions,
    currentQuestion,
    isLoading,
    pagination,

    // Actions
    fetchAllBanks,
    fetchSystemBanks,
    fetchMyBanks,
    fetchBankDetail,
    addBank,
    modifyBank,
    removeBank,
    fetchQuestions,
    fetchQuestionDetail,
    addQuestion,
    modifyQuestion,
    removeQuestion,
    reset
  }
})
