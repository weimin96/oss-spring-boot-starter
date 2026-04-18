<script setup lang="ts">
import {computed, ref} from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ObjectListPanel from '@/components/ObjectListPanel.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import {useResult} from '@/composables/useResult'
import {ossApi} from '@/api/oss'
import {normalizeObjectListItems} from '@/utils/objectExplorer'
import type {ObjectInfo} from '@/types'

// 列举对象
const listPath = ref('demo/')
const {status: listStatus, result: listResult, error: listError, execute: execList} = useResult<ObjectInfo[]>()
const listItems = computed(() => normalizeObjectListItems(listResult.value ?? []))
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">列举对象</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">列举指定路径下所有对象（含子路径）</p>

    <!-- 列举对象 -->
    <ApiCard method="GET" path="/oss/object/list" summary="列举指定路径下所有对象（含子路径）">
      <div class="mb-3">
        <p class="section-label">path</p>
        <input v-model="listPath" class="oss-input" placeholder="demo/"/>
      </div>
      <button class="btn btn-ghost" :disabled="listStatus === 'loading'"
              @click="execList(() => ossApi.query.listObjects(listPath))">
        <span v-if="listStatus === 'loading'" class="spinner"/>列举
      </button>
      <ResultPanel :status="listStatus" :result="listResult" :error="listError">
        <template #success>
          <ObjectListPanel
              title="对象列表"
              :items="listItems"
              empty-text="当前路径下没有对象"
          />
        </template>
      </ResultPanel>
    </ApiCard>
  </div>
</template>
